#include "PluginHost.h"
#include "ClapHost.h"
#include "Lv2Host.h"
#include "Vst3Bridge.h"

#include <android/log.h>
#include <time.h>
#include <algorithm>

#define LOG_TAG "MicPlugin.PluginHost"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace micplugin {

static int64_t nowNs() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1'000'000'000LL + ts.tv_nsec;
}

PluginHost::~PluginHost() {
    std::lock_guard<std::mutex> lock(uiMutex_);
    for (auto& p : chain_)        p->cleanup();
    for (auto& p : pendingChain_) p->cleanup();
}

void PluginHost::process(float* buf, int32_t frames, int32_t /*sampleRate*/) {
    // Commit pending chain (lock-free check)
    if (chainDirty_.load(std::memory_order_acquire)) {
        // Swap atomically — the UI thread has already built pendingChain_
        std::lock_guard<std::mutex> lock(uiMutex_);
        chain_ = pendingChain_;
        chainDirty_.store(false, std::memory_order_release);
    }

    for (auto& plugin : chain_) {
        if (!plugin->enabled.load(std::memory_order_relaxed)) continue;
        if (plugin->bypassed.load(std::memory_order_relaxed)) continue;

        int64_t t0 = nowNs();
        bool ok = plugin->process(buf, frames, 48000);
        int64_t elapsed = nowNs() - t0;

        if (!ok || elapsed > kWatchdogNs) {
            plugin->bypassed.store(true, std::memory_order_relaxed);
            LOGE("Plugin watchdog: %lld ns — auto-bypassed", (long long)elapsed);
        }
    }
}

jlong PluginHost::loadPlugin(const char* path, int32_t formatId, int32_t sampleRate) {
    std::lock_guard<std::mutex> lock(uiMutex_);

    std::shared_ptr<PluginInstance> instance;

    switch (formatId) {
        case 0: instance = ClapHost::load(path, sampleRate); break;
        case 1: instance = Lv2Host::load(path, sampleRate);  break;
        case 2: instance = Vst3Bridge::load(path, sampleRate); break;
        default:
            LOGE("Unknown plugin format: %d", formatId);
            return 0;
    }

    if (!instance) {
        LOGE("Failed to load plugin: %s", path);
        return 0;
    }

    instance->handle = nextHandle_++;
    instance->path   = path;

    pendingChain_.push_back(instance);
    chainDirty_.store(true, std::memory_order_release);

    LOGI("Loaded plugin: %s (handle=%lld)", path, (long long)instance->handle);
    return instance->handle;
}

void PluginHost::unloadPlugin(jlong handle) {
    std::lock_guard<std::mutex> lock(uiMutex_);
    pendingChain_.erase(
        std::remove_if(pendingChain_.begin(), pendingChain_.end(),
            [handle](const auto& p) {
                if (p->handle == handle) { p->cleanup(); return true; }
                return false;
            }),
        pendingChain_.end());
    chainDirty_.store(true, std::memory_order_release);
}

void PluginHost::setPluginParam(jlong handle, int32_t paramId, float value) {
    std::lock_guard<std::mutex> lock(uiMutex_);
    for (auto& p : pendingChain_) {
        if (p->handle == handle) {
            p->setParam(paramId, value);
            break;
        }
    }
}

void PluginHost::setPluginEnabled(jlong handle, bool enabled) {
    // Safe to write from UI because it's an atomic bool
    for (auto& p : chain_) {
        if (p->handle == handle) {
            p->enabled.store(enabled, std::memory_order_relaxed);
            return;
        }
    }
}

void PluginHost::reorderPlugin(jlong handle, int32_t newPos) {
    std::lock_guard<std::mutex> lock(uiMutex_);
    auto it = std::find_if(pendingChain_.begin(), pendingChain_.end(),
        [handle](const auto& p){ return p->handle == handle; });
    if (it == pendingChain_.end()) return;
    auto plugin = *it;
    pendingChain_.erase(it);
    newPos = std::max(0, std::min(newPos, (int)pendingChain_.size()));
    pendingChain_.insert(pendingChain_.begin() + newPos, plugin);
    chainDirty_.store(true, std::memory_order_release);
}

} // namespace micplugin
