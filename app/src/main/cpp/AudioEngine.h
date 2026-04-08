#pragma once

#include <oboe/Oboe.h>
#include <atomic>
#include <array>
#include <vector>
#include <memory>
#include <cmath>
#include <algorithm>
#include <cstring>

namespace micplugin {

// ─── Lock-free SPSC ring buffer (power-of-2 size) ────────────────────────────
template<size_t Capacity>
class SPSCRingBuffer {
    static_assert((Capacity & (Capacity - 1)) == 0, "Capacity must be power of 2");
    static constexpr size_t kMask = Capacity - 1;
public:
    void reset() {
        head_.store(0, std::memory_order_relaxed);
        tail_.store(0, std::memory_order_relaxed);
    }
    size_t available_read() const {
        return head_.load(std::memory_order_acquire) - tail_.load(std::memory_order_relaxed);
    }
    size_t available_write() const {
        return Capacity - available_read();
    }
    bool write(const float* src, size_t count) {
        if (available_write() < count) return false;
        size_t h = head_.load(std::memory_order_relaxed) & kMask;
        size_t first = std::min(count, Capacity - h);
        std::memcpy(&buf_[h], src, first * sizeof(float));
        if (count > first) std::memcpy(&buf_[0], src + first, (count - first) * sizeof(float));
        head_.fetch_add(count, std::memory_order_release);
        return true;
    }
    bool read(float* dst, size_t count) {
        if (available_read() < count) return false;
        size_t t = tail_.load(std::memory_order_relaxed) & kMask;
        size_t first = std::min(count, Capacity - t);
        std::memcpy(dst, &buf_[t], first * sizeof(float));
        if (count > first) std::memcpy(dst + first, &buf_[0], (count - first) * sizeof(float));
        tail_.fetch_add(count, std::memory_order_release);
        return true;
    }
private:
    alignas(64) std::array<float, Capacity> buf_{};
    alignas(64) std::atomic<size_t> head_{0};
    alignas(64) std::atomic<size_t> tail_{0};
};

// ─── Atomic parameter block (UI→Audio thread) ────────────────────────────────
struct EffectParams {
    // NoiseGate
    std::atomic<float> gateThresholdDb{-50.0f};
    std::atomic<float> gateAttackMs{5.0f};
    std::atomic<float> gateReleaseMs{150.0f};
    std::atomic<bool>  gateEnabled{true};

    // Equalizer — 10 bands: 31,63,125,250,500,1k,2k,4k,8k,16kHz
    std::atomic<float> eqGain[10];   // initialized in constructor
    std::atomic<bool>  eqEnabled{true};

    // Compressor
    std::atomic<float> compThresholdDb{-18.0f};
    std::atomic<float> compRatio{4.0f};
    std::atomic<float> compAttackMs{10.0f};
    std::atomic<float> compReleaseMs{100.0f};
    std::atomic<float> compMakeupDb{0.0f};
    std::atomic<bool>  compEnabled{true};

    // Reverb
    std::atomic<float> reverbMix{0.0f};
    std::atomic<float> reverbRoomSize{0.5f};
    std::atomic<float> reverbDamping{0.5f};
    std::atomic<bool>  reverbEnabled{false};

    // Pitch Shifter
    std::atomic<float> pitchSemitones{0.0f};
    std::atomic<bool>  pitchEnabled{false};

    // Metering (audio thread → UI)
    std::atomic<float> inputLevelDb{-100.0f};
    std::atomic<float> outputLevelDb{-100.0f};
    std::atomic<float> gainReductionDb{0.0f};

    // Master bypass
    std::atomic<bool>  masterBypass{false};

    // Monitoring — plays processed audio back through speaker/headphones
    std::atomic<bool>  monitoringEnabled{true};

    EffectParams() {
        for (auto& g : eqGain) g.store(0.0f, std::memory_order_relaxed);
    }
};

// ─── Biquad filter (Direct-Form II Transposed) ───────────────────────────────
struct BiquadFilter {
    float b0=1,b1=0,b2=0,a1=0,a2=0;
    float s1=0, s2=0;

    float process(float x) {
        float y = b0*x + s1;
        s1 = b1*x - a1*y + s2;
        s2 = b2*x - a2*y;
        return y;
    }
    void reset() { s1=s2=0.0f; }
    void setPeak(float fc, float gainDb, float Q, float sr);
    void setLowShelf(float fc, float gainDb, float sr);
    void setHighShelf(float fc, float gainDb, float sr);
};

// ─── Reverb allpass ───────────────────────────────────────────────────────────
struct AllpassFilter {
    std::vector<float> buf;
    int idx=0;
    float g=0.5f;
    AllpassFilter(int size) : buf(size,0.0f) {}
    float process(float x) {
        float d = buf[idx];
        float y = -x + d;
        buf[idx] = x + d * g;
        if (++idx >= (int)buf.size()) idx=0;
        return y;
    }
};

// ─── Reverb comb ─────────────────────────────────────────────────────────────
struct CombFilter {
    std::vector<float> buf;
    int idx=0;
    float feedback=0.84f, damp=0.2f, filterStore=0.0f;
    CombFilter(int size) : buf(size,0.0f) {}
    float process(float x) {
        float output = buf[idx];
        filterStore = output*(1.0f-damp) + filterStore*damp;
        buf[idx] = x + filterStore*feedback;
        if (++idx >= (int)buf.size()) idx=0;
        return output;
    }
};

class PluginHost;

// ─── Main AudioEngine ─────────────────────────────────────────────────────────
class AudioEngine : public oboe::AudioStreamDataCallback,
                    public oboe::AudioStreamErrorCallback {
public:
    AudioEngine();
    ~AudioEngine();

    bool start();
    void stop();
    bool isRunning() const { return running_.load(std::memory_order_relaxed); }

    EffectParams& params() { return params_; }
    void setPluginHost(PluginHost* h) { pluginHost_ = h; }

    int32_t sampleRate()    const { return sampleRate_; }
    int32_t framesPerBurst() const { return framesPerBurst_; }

    // oboe callbacks
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* stream, void* data, int32_t frames) override;
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    bool openStreams();
    void closeStreams();
    void rebuildEQ();

    // DSP (all called from audio thread, no allocations)
    void processEffectsChain(float* buf, int32_t frames);
    void dsp_noiseGate(float* buf, int32_t frames);
    void dsp_eq(float* buf, int32_t frames);
    void dsp_compressor(float* buf, int32_t frames);
    void dsp_reverb(float* buf, int32_t frames);
    void dsp_pitchShift(float* buf, int32_t frames);
    void measureLevel(const float* buf, int32_t frames, std::atomic<float>& out);

    static inline float dBToLin(float db) { return std::pow(10.0f, db * 0.05f); }
    static inline float linTodB(float lin) {
        return lin < 1e-10f ? -200.0f : 20.0f * std::log10(lin);
    }

    // Streams
    std::shared_ptr<oboe::AudioStream> inputStream_, outputStream_;
    std::atomic<bool> running_{false};

    int32_t sampleRate_    = 48000;
    int32_t framesPerBurst_ = 128;

    // Ring buffer: ~0.5 sec @ 48kHz
    static constexpr size_t kRingSize = 32768;
    SPSCRingBuffer<kRingSize> ring_;

    // Params (UI writes, audio thread reads)
    EffectParams params_;
    PluginHost*  pluginHost_ = nullptr;

    // Work buffer
    static constexpr int kMaxFrames = 1024;
    // workBuf_ removed (unused)

    // ─ Gate state ─
    float gateEnv_  = 0.0f;
    float gateRms_  = 0.0f;

    // ─ EQ state ─
    static constexpr int kEqBands = 10;
    static constexpr float kEqFreqs[kEqBands] = {31,63,125,250,500,1000,2000,4000,8000,16000};
    BiquadFilter eqFilters_[kEqBands];
    float eqGainCache_[kEqBands]{};
    bool  eqDirty_ = true;

    // ─ Compressor state ─
    float compEnv_ = 0.0f;
    float compGainReduction_ = 0.0f;

    // ─ Reverb state (Schroeder) ─
    // 4 comb + 2 allpass, scaled for 48kHz
    static constexpr int kCombSizes[4] = {1116,1188,1277,1356};
    static constexpr int kApSizes[2]   = {556, 441};
    CombFilter   combs_[4] = {CombFilter(1116),CombFilter(1188),CombFilter(1277),CombFilter(1356)};
    AllpassFilter aps_[2]  = {AllpassFilter(556), AllpassFilter(441)};

    // ─ Pitch shifter state ─
    static constexpr int kGrainSize = 2048;
    static constexpr int kHopOut    = 512;
    float pitchIn_[kGrainSize*2]{};
    float pitchOut_[kGrainSize*2]{};
    float pitchWindow_[kGrainSize]{};
    int   pitchWritePos_  = 0;
    float pitchReadFrac_  = 0.0f;
    // pitchRateCache_ removed (unused)
};

} // namespace micplugin
