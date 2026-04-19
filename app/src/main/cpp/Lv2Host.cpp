#include "Lv2Host.h"
#include "TtlParser.h"

#if __has_include(<lv2/core/lv2.h>)
#  include <lv2/core/lv2.h>
#  include <lv2/urid/urid.h>
#  include <lv2/options/options.h>
#else
#  include <lv2/lv2plug.in/ns/lv2core/lv2.h>
#  include <lv2/lv2plug.in/ns/ext/urid/urid.h>
#  include <lv2/lv2plug.in/ns/ext/options/options.h>
#endif

#include <dlfcn.h>
#include <android/log.h>
#include <vector>
#include <map>
#include <cstring>
#include <cmath>
#include <string>
#include <mutex>

#define LOG_TAG "MicPlugin.LV2"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace micplugin {

// ─── Minimal URID map (required by most LV2 plugins) ─────────────────────────
static std::map<std::string, LV2_URID> gUridMap;
static std::mutex gUridMutex;
static LV2_URID   gNextUrid = 1;

static LV2_URID uridMap(LV2_URID_Map_Handle, const char* uri) {
    std::lock_guard<std::mutex> lock(gUridMutex);
    auto it = gUridMap.find(uri);
    if (it != gUridMap.end()) return it->second;
    LV2_URID id = gNextUrid++;
    gUridMap[uri] = id;
    return id;
}

static LV2_URID_Map gUridMapData = { nullptr, uridMap };

// Minimal options (empty list)
static LV2_Options_Option gOptions[] = {
    { LV2_OPTIONS_INSTANCE, 0, 0, 0, 0, nullptr }
};

// Feature list provided to plugin
static LV2_Feature gFeatUrid    = { LV2_URID__map,    &gUridMapData };
static LV2_Feature gFeatOptions = { LV2_OPTIONS__options, gOptions   };
static const LV2_Feature* kLv2Features[] = {
    &gFeatUrid, &gFeatOptions, nullptr
};

// ─── Plugin instance ──────────────────────────────────────────────────────────
struct Lv2PluginInstance : public PluginInstance {
    void*                 dlib     = nullptr;
    const LV2_Descriptor* desc     = nullptr;
    LV2_Handle            lvInst   = nullptr;
    int32_t               audioIn  = -1;
    int32_t               audioOut = -1;

    static constexpr int kMaxPorts = 128;
    float controlPorts[kMaxPorts]{};
    bool  isControl[kMaxPorts]{};
    int   portCount = 0;

    std::vector<LV2PortInfo> portInfos;

    float inputBuf[4096]{};
    float outputBuf[4096]{};

    bool process(float* buf, int32_t frames, int32_t) override {
        if (!lvInst || !desc || !desc->run) return false;
        std::memcpy(inputBuf, buf, frames * sizeof(float));
        if (audioIn  >= 0 && audioIn  < kMaxPorts)
            desc->connect_port(lvInst, audioIn,  inputBuf);
        if (audioOut >= 0 && audioOut < kMaxPorts)
            desc->connect_port(lvInst, audioOut, outputBuf);
        desc->run(lvInst, (uint32_t)frames);
        if (audioOut >= 0) std::memcpy(buf, outputBuf, frames * sizeof(float));
        return true;
    }

    void setParam(int32_t id, float value) override {
        if (id >= 0 && id < kMaxPorts && isControl[id])
            controlPorts[id] = value;
    }

    void cleanup() override {
        if (desc && lvInst) {
            if (desc->deactivate) desc->deactivate(lvInst);
            if (desc->cleanup)    desc->cleanup(lvInst);
            lvInst = nullptr;
        }
        if (dlib) { dlclose(dlib); dlib = nullptr; }
    }
};

std::shared_ptr<PluginInstance> Lv2Host::load(const char* soPath, int32_t sampleRate) {
    void* dlib = dlopen(soPath, RTLD_NOW | RTLD_LOCAL);
    if (!dlib) { LOGE("dlopen failed: %s — %s", soPath, dlerror()); return nullptr; }

    using LV2_Descriptor_Fn = const LV2_Descriptor*(uint32_t);
    auto* lv2_descriptor = reinterpret_cast<LV2_Descriptor_Fn*>(dlsym(dlib, "lv2_descriptor"));
    if (!lv2_descriptor) { LOGE("lv2_descriptor not found"); dlclose(dlib); return nullptr; }

    const LV2_Descriptor* desc = lv2_descriptor(0);
    if (!desc) { LOGE("No descriptor at index 0"); dlclose(dlib); return nullptr; }
    LOGI("LV2 plugin: %s", desc->URI);

    LV2_Handle inst = desc->instantiate(desc, (double)sampleRate, soPath, kLv2Features);
    if (!inst) { LOGE("instantiate failed (missing features?)"); dlclose(dlib); return nullptr; }

    auto instance      = std::make_shared<Lv2PluginInstance>();
    instance->dlib     = dlib;
    instance->desc     = desc;
    instance->lvInst   = inst;
    instance->format   = PluginFormat::LV2;
    instance->path     = soPath;

    // Parse TTL
    instance->portInfos = TtlParser::parseFromSo(soPath);
    LOGI("TTL ports: %zu", instance->portInfos.size());

    // Set audio/control ports from TTL
    instance->audioIn  = -1;
    instance->audioOut = -1;
    for (auto& p : instance->portInfos) {
        if (!p.isControl && p.isInput  && instance->audioIn  < 0) instance->audioIn  = p.index;
        if (!p.isControl && !p.isInput && instance->audioOut < 0) instance->audioOut = p.index;
        if (p.isControl && p.index >= 0 && p.index < instance->kMaxPorts) {
            instance->isControl[p.index]    = true;
            instance->controlPorts[p.index] = p.defaultVal;
        }
    }

    // Fallback port convention if no TTL
    if (instance->portInfos.empty()) {
        instance->audioIn  = 0;
        instance->audioOut = 1;
        for (int i = 2; i < 64; i++) {
            instance->isControl[i] = true;
            instance->controlPorts[i] = 0.f;
        }
    }

    LOGI("Audio ports: in=%d out=%d", instance->audioIn, instance->audioOut);
    instance->portCount = instance->kMaxPorts;

    // Connect all ports
    for (int i = 0; i < instance->kMaxPorts; i++) {
        if (instance->isControl[i])
            desc->connect_port(inst, (uint32_t)i, &instance->controlPorts[i]);
    }

    // Connect dummy latency/output control ports
    for (auto& p : instance->portInfos) {
        if (!p.isControl || p.isInput) continue;
        // Output control port (latency etc) — connect to dummy
        if (p.index >= 0 && p.index < instance->kMaxPorts && !instance->isControl[p.index]) {
            instance->controlPorts[p.index] = 0.f;
            desc->connect_port(inst, (uint32_t)p.index, &instance->controlPorts[p.index]);
        }
    }

    if (desc->activate) desc->activate(inst);
    return instance;
}

std::string Lv2Host::getParams(const std::shared_ptr<PluginInstance>& inst) {
    auto* li = static_cast<Lv2PluginInstance*>(inst.get());
    if (!li) return "[]";

    std::string json = "[";
    bool first = true;

    if (!li->portInfos.empty()) {
        for (auto& p : li->portInfos) {
            if (!p.isControl || !p.isInput) continue;
            if (!first) json += ",";
            first = false;
            std::string name = p.name.empty() ? ("p" + std::to_string(p.index)) : p.name;
            // Escape quotes in name
            std::string escaped;
            for (char c : name) { if (c == '"') escaped += '\\'; escaped += c; }
            json += "{\"id\":"     + std::to_string(p.index)      + ",";
            json += "\"name\":\""  + escaped                       + "\",";
            json += "\"min\":"     + std::to_string(p.minimum)    + ",";
            json += "\"max\":"     + std::to_string(p.maximum)    + ",";
            json += "\"default\":" + std::to_string(p.defaultVal) + "}";
        }
    } else {
        for (int i = 2; i < li->kMaxPorts; i++) {
            if (!li->isControl[i]) continue;
            if (!first) json += ",";
            first = false;
            json += "{\"id\":"     + std::to_string(i) + ",";
            json += "\"name\":\"Control " + std::to_string(i - 1) + "\",";
            json += "\"min\":0.0,\"max\":1.0,";
            json += "\"default\":" + std::to_string(li->controlPorts[i]) + "}";
        }
    }
    json += "]";
    return json;
}

} // namespace micplugin
