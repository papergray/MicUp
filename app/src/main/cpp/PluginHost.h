#pragma once

#include <jni.h>
#include <atomic>
#include <vector>
#include <memory>
#include <mutex>
#include <string>
#include <cstdint>

namespace micplugin {

enum class PluginFormat { CLAP = 0, LV2 = 1, VST3 = 2 };

struct PluginInstance {
    jlong         handle;
    PluginFormat  format;
    std::string   path;
    std::atomic<bool> enabled{true};
    std::atomic<bool> bypassed{false};  // auto-bypass on timeout

    // Subclass data pointers
    void* nativeHandle = nullptr;

    virtual ~PluginInstance() = default;
    virtual bool process(float* buf, int32_t frames, int32_t sr) = 0;
    virtual void setParam(int32_t id, float value) {}
    virtual void cleanup() {}
};

struct ClapPluginInstance;
struct Lv2PluginInstance;
struct Vst3PluginInstance;

class PluginHost {
public:
    PluginHost() = default;
    ~PluginHost();

    // Called from audio thread (no locks — uses copy-on-write chain)
    void process(float* buf, int32_t frames, int32_t sampleRate);

    // Called from UI thread
    jlong loadPlugin(const char* path, int32_t formatId, int32_t sampleRate);
    void  unloadPlugin(jlong handle);
    void  setPluginParam(jlong handle, int32_t paramId, float value);
    void  setPluginEnabled(jlong handle, bool enabled);
    void  reorderPlugin(jlong handle, int32_t newPos);
    std::shared_ptr<PluginInstance> findPlugin(jlong handle);

    static constexpr int64_t kWatchdogNs = 2'000'000LL; // 2ms

private:
    // Swap chain safely from UI thread
    void commitChain();

    std::vector<std::shared_ptr<PluginInstance>> chain_;        // audio thread reads
    std::vector<std::shared_ptr<PluginInstance>> pendingChain_; // UI thread writes
    std::atomic<bool> chainDirty_{false};
    std::mutex uiMutex_;

    jlong nextHandle_ = 1;
};

} // namespace micplugin
