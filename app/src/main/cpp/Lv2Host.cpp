#include "Lv2Host.h"

// lv2 >= 1.18.0 reorganized headers to lv2/core/lv2.h
// Fall back to old path for compatibility
#if __has_include(<lv2/core/lv2.h>)
#  include <lv2/core/lv2.h>
#else
#  include <lv2/lv2plug.in/ns/lv2core/lv2.h>
#endif
#include <dlfcn.h>
#include <android/log.h>
#include <vector>
#include <cstring>
#include <cmath>

#define LOG_TAG "MicPlugin.LV2"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace micplugin {

// ─── LV2 feature table (minimal: only required features) ────────────────────
static const LV2_Feature* kLv2Features[] = { nullptr }; // empty feature table

struct Lv2PluginInstance : public PluginInstance {
    void*              dlib       = nullptr;
    const LV2_Descriptor* desc   = nullptr;
    LV2_Handle         lvInst    = nullptr;
    int32_t            audioIn   = -1;
    int32_t            audioOut  = -1;
    // Control port storage (up to 64 ports)
    static constexpr int kMaxPorts = 64;
    float              controlPorts[kMaxPorts]{};
    bool               isControl[kMaxPorts]{};
    int                portCount  = 0;

    float inputBuf[4096]{};
    float outputBuf[4096]{};

    bool process(float* buf, int32_t frames, int32_t /*sr*/) override {
        if (!lvInst || !desc || !desc->run) return false;

        std::memcpy(inputBuf, buf, frames * sizeof(float));

        // Connect audio ports
        if (audioIn  >= 0) desc->connect_port(lvInst, audioIn,  inputBuf);
        if (audioOut >= 0) desc->connect_port(lvInst, audioOut, outputBuf);

        desc->run(lvInst, (uint32_t)frames);

        if (audioOut >= 0)
            std::memcpy(buf, outputBuf, frames * sizeof(float));
        return true;
    }

    void setParam(int32_t id, float value) override {
        if (id >= 0 && id < portCount && isControl[id]) {
            controlPorts[id] = value;
        }
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
    if (!dlib) {
        LOGE("LV2 dlopen failed: %s — %s", soPath, dlerror());
        return nullptr;
    }

    // LV2 descriptor function
    using LV2_Descriptor_Fn = const LV2_Descriptor*(uint32_t);
    auto* lv2_descriptor = reinterpret_cast<LV2_Descriptor_Fn*>(
        dlsym(dlib, "lv2_descriptor"));
    if (!lv2_descriptor) {
        LOGE("lv2_descriptor not found in: %s", soPath);
        dlclose(dlib);
        return nullptr;
    }

    const LV2_Descriptor* desc = lv2_descriptor(0);
    if (!desc) {
        LOGE("No LV2 descriptor at index 0 in: %s", soPath);
        dlclose(dlib);
        return nullptr;
    }

    LOGI("LV2 plugin: %s", desc->URI);

    LV2_Handle inst = desc->instantiate(desc, (double)sampleRate, soPath, kLv2Features);
    if (!inst) {
        LOGE("LV2 instantiate failed");
        dlclose(dlib);
        return nullptr;
    }

    auto instance      = std::make_shared<Lv2PluginInstance>();
    instance->dlib     = dlib;
    instance->desc     = desc;
    instance->lvInst   = inst;
    instance->format   = PluginFormat::LV2;

    // Minimal port scanning: assume port 0=audioIn, port 1=audioOut
    // Real implementation would parse manifest.ttl; here we use convention
    instance->audioIn  = 0;
    instance->audioOut = 1;

    // Connect control ports (dummy values)
    for (int i = 2; i < instance->kMaxPorts; i++) {
        instance->isControl[i] = true;
        instance->controlPorts[i] = 0.0f;
        // Attempt to connect; plugin may ignore invalid ports
        desc->connect_port(inst, (uint32_t)i, &instance->controlPorts[i]);
    }

    if (desc->activate) desc->activate(inst);
    return instance;
}

} // namespace micplugin
