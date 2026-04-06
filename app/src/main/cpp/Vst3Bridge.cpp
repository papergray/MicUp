#include "Vst3Bridge.h"
#include <dlfcn.h>
#include <android/log.h>
#include <cstring>
#include <cstdint>
#include <vector>

#define LOG_TAG "MicPlugin.VST3"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace micplugin {

// ─── Minimal vestige-style VST3 ABI definitions (GPL-compatible) ─────────────
// These definitions reverse-engineer the VST3 C++ vtable ABI without
// including Steinberg's proprietary SDK.

typedef uint32_t VSTGUID[4];

struct IUnknown_vst {
    virtual int32_t queryInterface(const VSTGUID& iid, void** obj) = 0;
    virtual uint32_t addRef()  = 0;
    virtual uint32_t release() = 0;
};

// IPluginFactory vtable (index 3 = createInstance)
struct IPluginFactory {
    virtual int32_t queryInterface(const VSTGUID&, void**) = 0;
    virtual uint32_t addRef() = 0;
    virtual uint32_t release() = 0;
    virtual int32_t getFactoryInfo(void* info) = 0;
    virtual int32_t countClasses() = 0;
    virtual int32_t getClassInfo(int32_t idx, void* info) = 0;
    virtual int32_t createInstance(const char* cid, const char* iid, void** obj) = 0;
};

using GetPluginFactory_fn = IPluginFactory*(*)();

// ─── VST3 plugin instance wrapper ────────────────────────────────────────────
struct Vst3PluginInstance : public PluginInstance {
    void*            dlib    = nullptr;
    IPluginFactory*  factory = nullptr;

    std::vector<float> inputBuf, outputBuf;

    bool process(float* buf, int32_t frames, int32_t /*sr*/) override {
        // VST3 experimental: pass-through with warning
        // A full implementation requires IComponent + IAudioProcessor
        // which needs the full Steinberg SDK or a complete vestige implementation.
        // For now, audio passes through unmodified.
        return true;
    }

    void cleanup() override {
        if (factory) { factory->release(); factory = nullptr; }
        if (dlib)    { dlclose(dlib);       dlib    = nullptr; }
    }
};

std::shared_ptr<PluginInstance> Vst3Bridge::load(const char* soPath, int32_t sampleRate) {
    void* dlib = dlopen(soPath, RTLD_NOW | RTLD_LOCAL);
    if (!dlib) {
        LOGE("VST3 dlopen failed: %s — %s", soPath, dlerror());
        return nullptr;
    }

    auto* getFactory = reinterpret_cast<GetPluginFactory_fn>(
        dlsym(dlib, "GetPluginFactory"));
    if (!getFactory) {
        LOGE("GetPluginFactory not found in: %s", soPath);
        dlclose(dlib);
        return nullptr;
    }

    IPluginFactory* factory = getFactory();
    if (!factory) {
        LOGE("GetPluginFactory returned null");
        dlclose(dlib);
        return nullptr;
    }

    int32_t count = factory->countClasses();
    LOGI("VST3 factory has %d classes in: %s", count, soPath);

    auto instance         = std::make_shared<Vst3PluginInstance>();
    instance->dlib        = dlib;
    instance->factory     = factory;
    instance->format      = PluginFormat::VST3;
    instance->inputBuf.resize(4096, 0.0f);
    instance->outputBuf.resize(4096, 0.0f);

    LOGI("VST3 plugin loaded (experimental pass-through): %s", soPath);
    return instance;
}

} // namespace micplugin
