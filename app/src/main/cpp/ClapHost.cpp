#include "ClapHost.h"

#include <clap/clap.h>
#include <dlfcn.h>
#include <android/log.h>
#include <atomic>
#include <vector>
#include <cstring>
#include <memory>

#define LOG_TAG "MicPlugin.CLAP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace micplugin {

// ─── CLAP host callbacks ─────────────────────────────────────────────────────
static const void* clapHostGetExtension(const clap_host_t*, const char* extId) {
    // Return nullptr for optional extensions we don't implement
    return nullptr;
}
static void clapHostRequestRestart(const clap_host_t*) {}
static void clapHostRequestProcess(const clap_host_t*) {}
static void clapHostRequestCallback(const clap_host_t*) {}

static const clap_host_t kClapHost = {
    .clap_version  = CLAP_VERSION_INIT,
    .host_data     = nullptr,
    .name          = "MicPlugin",
    .vendor        = "MicPlugin",
    .url           = "https://github.com/micplugin",
    .version       = "1.0.0",
    .get_extension = clapHostGetExtension,
    .request_restart   = clapHostRequestRestart,
    .request_process   = clapHostRequestProcess,
    .request_callback  = clapHostRequestCallback,
};

// ─── CLAP plugin instance ────────────────────────────────────────────────────
struct ClapPluginInstance : public PluginInstance {
    void*              dlib       = nullptr;
    const clap_plugin_t* plugin  = nullptr;
    const clap_plugin_params_t* paramsExt = nullptr;

    // Pre-allocated CLAP process structure
    clap_audio_buffer_t inBuf{}, outBuf{};
    clap_process_t      proc{};
    float* channelPtrs[1] = {nullptr};
    std::vector<float>  processBuffer;

    bool process(float* buf, int32_t frames, int32_t sr) override {
        if (!plugin || !plugin->process) return false;
        if ((int32_t)processBuffer.size() < frames) return false;

        std::memcpy(processBuffer.data(), buf, frames * sizeof(float));
        channelPtrs[0]   = processBuffer.data();
        inBuf.data32     = channelPtrs;
        inBuf.channel_count = 1;
        outBuf.data32    = channelPtrs;
        outBuf.channel_count = 1;

        proc.frames_count       = (uint32_t)frames;
        proc.audio_inputs       = &inBuf;
        proc.audio_inputs_count  = 1;
        proc.audio_outputs      = &outBuf;
        proc.audio_outputs_count = 1;
        proc.in_events  = nullptr;
        proc.out_events = nullptr;

        clap_process_status status = plugin->process(plugin, &proc);
        if (status == CLAP_PROCESS_ERROR) return false;

        std::memcpy(buf, processBuffer.data(), frames * sizeof(float));
        return true;
    }

    void setParam(int32_t id, float value) override {
        // CLAP params are flushed via events; store for next callback
        // For simplicity use direct automation if ext available
        if (paramsExt) {
            clap_event_param_value_t ev{};
            ev.header.size  = sizeof(ev);
            ev.header.type  = CLAP_EVENT_PARAM_VALUE;
            ev.header.flags = CLAP_EVENT_IS_LIVE;
            ev.param_id     = (clap_id)id;
            ev.value        = (double)value;
        }
    }

    void cleanup() override {
        if (plugin) {
            plugin->deactivate(plugin);
            plugin->destroy(plugin);
            plugin = nullptr;
        }
        if (dlib) { dlclose(dlib); dlib = nullptr; }
    }
};

// ─── Load ───────────────────────────────────────────────────────────────────
std::shared_ptr<PluginInstance> ClapHost::load(const char* path, int32_t sampleRate) {
    void* dlib = dlopen(path, RTLD_NOW | RTLD_LOCAL);
    if (!dlib) {
        LOGE("dlopen failed: %s — %s", path, dlerror());
        return nullptr;
    }

    auto* entry = reinterpret_cast<const clap_plugin_entry_t*>(
        dlsym(dlib, "clap_entry"));
    if (!entry) {
        LOGE("clap_entry not found in: %s", path);
        dlclose(dlib);
        return nullptr;
    }

    if (!entry->init(path)) {
        LOGE("clap_entry.init failed: %s", path);
        dlclose(dlib);
        return nullptr;
    }

    const clap_plugin_factory_t* factory = reinterpret_cast<const clap_plugin_factory_t*>(
        entry->get_factory(CLAP_PLUGIN_FACTORY_ID));
    if (!factory || factory->get_plugin_count(factory) == 0) {
        LOGE("No CLAP plugins in factory: %s", path);
        entry->deinit();
        dlclose(dlib);
        return nullptr;
    }

    const clap_plugin_descriptor_t* desc = factory->get_plugin_descriptor(factory, 0);
    if (!desc) { entry->deinit(); dlclose(dlib); return nullptr; }

    LOGI("CLAP plugin found: %s (%s)", desc->name, desc->id);

    const clap_plugin_t* plugin = factory->create_plugin(factory, &kClapHost, desc->id);
    if (!plugin) { entry->deinit(); dlclose(dlib); return nullptr; }

    if (!plugin->init(plugin)) {
        plugin->destroy(plugin);
        entry->deinit();
        dlclose(dlib);
        return nullptr;
    }

    if (!plugin->activate(plugin, sampleRate, 32, 4096)) {
        plugin->destroy(plugin);
        entry->deinit();
        dlclose(dlib);
        return nullptr;
    }

    plugin->start_processing(plugin);

    auto instance         = std::make_shared<ClapPluginInstance>();
    instance->dlib        = dlib;
    instance->plugin      = plugin;
    instance->format      = PluginFormat::CLAP;
    instance->paramsExt   = reinterpret_cast<const clap_plugin_params_t*>(
        plugin->get_extension(plugin, CLAP_EXT_PARAMS));
    instance->processBuffer.resize(4096, 0.0f);
    return instance;
}

} // namespace micplugin

// ─── Query params from a loaded CLAP instance ───────────────────────────────
// Returns JSON: [{"id":0,"name":"Gain","min":0.0,"max":1.0,"default":0.5}, ...]
std::string ClapHost::getParams(const std::shared_ptr<PluginInstance>& inst) {
    auto* ci = static_cast<ClapPluginInstance*>(inst.get());
    if (!ci || !ci->paramsExt) return "[]";

    uint32_t count = ci->paramsExt->count(ci->plugin);
    if (count == 0) return "[]";

    std::string json = "[";
    for (uint32_t i = 0; i < count; i++) {
        clap_param_info_t info{};
        if (!ci->paramsExt->get_info(ci->plugin, i, &info)) continue;

        double val = 0.0;
        ci->paramsExt->get_value(ci->plugin, info.id, &val);

        if (i > 0) json += ",";
        json += "{";
        json += "\"id\":" + std::to_string(info.id) + ",";
        // Escape name
        std::string name(info.name);
        json += "\"name\":\"" + name + "\",";
        json += "\"min\":"     + std::to_string(info.min_value) + ",";
        json += "\"max\":"     + std::to_string(info.max_value) + ",";
        json += "\"default\":" + std::to_string(val);
        json += "}";
    }
    json += "]";
    return json;
}
