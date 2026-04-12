#include "AudioEngine.h"
#include "PluginHost.h"
#include "ClapHost.h"
#include "Vst3Bridge.h"
#include "Lv2Host.h"

#include <jni.h>
#include <android/log.h>
#include <pthread.h>
#include <cmath>
#include <algorithm>
#include <numeric>

#define LOG_TAG "MicPlugin.Engine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace micplugin {

// ─── BiquadFilter coefficient computation ────────────────────────────────────
void BiquadFilter::setPeak(float fc, float gainDb, float Q, float sr) {
    float A  = std::pow(10.0f, gainDb / 40.0f);
    float w0 = 2.0f * M_PI * fc / sr;
    float cosW = std::cos(w0);
    float sinW = std::sin(w0);
    float alpha = sinW / (2.0f * Q);
    float a0inv = 1.0f / (1.0f + alpha / A);
    b0 = (1.0f + alpha * A) * a0inv;
    b1 = (-2.0f * cosW)     * a0inv;
    b2 = (1.0f - alpha * A) * a0inv;
    a1 = b1;
    a2 = (1.0f - alpha / A) * a0inv;
}

void BiquadFilter::setLowShelf(float fc, float gainDb, float sr) {
    float A  = std::pow(10.0f, gainDb / 40.0f);
    float w0 = 2.0f * M_PI * fc / sr;
    float cosW = std::cos(w0);
    float sinW = std::sin(w0);
    float beta = std::sqrt(A) * sinW;
    float a0inv = 1.0f / ((A+1) + (A-1)*cosW + beta);
    b0 = A * ((A+1) - (A-1)*cosW + beta)          * a0inv;
    b1 = 2.0f * A * ((A-1) - (A+1)*cosW)          * a0inv;
    b2 = A * ((A+1) - (A-1)*cosW - beta)           * a0inv;
    a1 = -2.0f * ((A-1) + (A+1)*cosW)              * a0inv;
    a2 = ((A+1) + (A-1)*cosW - beta)               * a0inv;
}

void BiquadFilter::setHighShelf(float fc, float gainDb, float sr) {
    float A  = std::pow(10.0f, gainDb / 40.0f);
    float w0 = 2.0f * M_PI * fc / sr;
    float cosW = std::cos(w0);
    float sinW = std::sin(w0);
    float beta = std::sqrt(A) * sinW;
    float a0inv = 1.0f / ((A+1) - (A-1)*cosW + beta);
    b0 = A * ((A+1) + (A-1)*cosW + beta)          * a0inv;
    b1 = -2.0f * A * ((A-1) + (A+1)*cosW)         * a0inv;
    b2 = A * ((A+1) + (A-1)*cosW - beta)           * a0inv;
    a1 = 2.0f * ((A-1) - (A+1)*cosW)              * a0inv;
    a2 = ((A+1) - (A-1)*cosW - beta)              * a0inv;
}

// ─── AudioEngine ─────────────────────────────────────────────────────────────
AudioEngine::AudioEngine() {
    // Init Hann window for pitch shifter
    for (int i = 0; i < kGrainSize; i++) {
        pitchWindow_[i] = 0.5f * (1.0f - std::cos(2.0f * M_PI * i / kGrainSize));
    }
    std::fill(pitchIn_,  pitchIn_  + kGrainSize*2, 0.0f);
    std::fill(pitchOut_, pitchOut_ + kGrainSize*2, 0.0f);
}

AudioEngine::~AudioEngine() {
    stop();
}

bool AudioEngine::start() {
    if (running_.load()) return true;
    ring_.reset();
    if (!openStreams()) return false;
    running_.store(true, std::memory_order_release);
    LOGI("AudioEngine started: %dHz, %d frames/burst", sampleRate_, framesPerBurst_);
    return true;
}

void AudioEngine::stop() {
    running_.store(false, std::memory_order_release);
    closeStreams();
}

bool AudioEngine::openStreams() {
    oboe::AudioStreamBuilder builder;

    // ─ Input stream (microphone capture) ─────────────────────────────────────
    auto result = builder
        .setDirection(oboe::Direction::Input)
        ->setInputPreset(oboe::InputPreset::VoiceCommunication)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(1)
        ->setSampleRate(48000)
        ->openStream(inputStream_);

    if (result != oboe::Result::OK) {
        LOGE("Failed to open input stream: %s", oboe::convertToText(result));
        return false;
    }

    sampleRate_    = inputStream_->getSampleRate();
    framesPerBurst_ = inputStream_->getFramesPerBurst();
    LOGI("Input stream opened: %dHz, %d frames/burst", sampleRate_, framesPerBurst_);

    // ─ Output stream (playback → VOICE_COMMUNICATION) ────────────────────────
    builder.setDirection(oboe::Direction::Output)
           ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
           ->setSharingMode(oboe::SharingMode::Exclusive)
           ->setFormat(oboe::AudioFormat::Float)
           ->setChannelCount(1)
           ->setSampleRate(sampleRate_)
           ->setFramesPerDataCallback(framesPerBurst_)
           ->setDataCallback(this)
           ->setErrorCallback(this)
           ->setUsage(oboe::Usage::VoiceCommunication)
           ->setContentType(oboe::ContentType::Speech);

    result = builder.openStream(outputStream_);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open output stream: %s", oboe::convertToText(result));
        inputStream_->close();
        return false;
    }

    // Boost audio thread priority (SCHED_FIFO P96 via Oboe)
    outputStream_->setBufferSizeInFrames(framesPerBurst_ * 2);

    // Start both streams
    inputStream_->requestStart();
    outputStream_->requestStart();

    eqDirty_ = true;
    rebuildEQ();
    return true;
}

void AudioEngine::closeStreams() {
    if (outputStream_) { outputStream_->requestStop(); outputStream_->close(); outputStream_.reset(); }
    if (inputStream_)  { inputStream_->requestStop();  inputStream_->close();  inputStream_.reset();  }
}

void AudioEngine::rebuildEQ() {
    for (int i = 0; i < kEqBands; i++) {
        float gain = params_.eqGain[i].load(std::memory_order_relaxed);
        if (i == 0)
            eqFilters_[i].setLowShelf(kEqFreqs[i], gain, (float)sampleRate_);
        else if (i == kEqBands - 1)
            eqFilters_[i].setHighShelf(kEqFreqs[i], gain, (float)sampleRate_);
        else
            eqFilters_[i].setPeak(kEqFreqs[i], gain, 1.0f, (float)sampleRate_);
        eqGainCache_[i] = gain;
    }
    eqDirty_ = false;
}

// ─── Audio callback (output stream drives everything) ────────────────────────
oboe::DataCallbackResult AudioEngine::onAudioReady(
    oboe::AudioStream* stream, void* audioData, int32_t numFrames) {

    auto* out = static_cast<float*>(audioData);

    if (!running_.load(std::memory_order_acquire)) {
        std::fill(out, out + numFrames, 0.0f);
        return oboe::DataCallbackResult::Continue;
    }

    // Read from input stream (non-blocking, 0ns timeout)
    oboe::ResultWithValue<int32_t> readResult =
        inputStream_->read(out, numFrames, 0);

    int32_t framesRead = (readResult) ? readResult.value() : 0;

    if (framesRead < numFrames) {
        // Zero-fill remaining frames to avoid glitches
        std::fill(out + framesRead, out + numFrames, 0.0f);
    }

    // Measure input level (no log, no alloc — safe for RT)
    measureLevel(out, numFrames, params_.inputLevelDb);

    // Process effects chain
    if (!params_.masterBypass.load(std::memory_order_relaxed)) {
        processEffectsChain(out, numFrames);
    }

    // Process external plugins
    if (pluginHost_) {
        pluginHost_->process(out, numFrames, sampleRate_);
    }

    // Measure output level
    measureLevel(out, numFrames, params_.outputLevelDb);

    // Silence output if monitoring (sidetone) is disabled
    if (!params_.monitoringEnabled.load(std::memory_order_relaxed)) {
        std::fill(out, out + numFrames, 0.0f);
    }

    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) {
    LOGE("Audio stream error: %s — restarting", oboe::convertToText(error));
    if (running_.load()) {
        closeStreams();
        openStreams();
    }
}

void AudioEngine::measureLevel(const float* buf, int32_t frames, std::atomic<float>& out) {
    float rms = 0.0f;
    for (int i = 0; i < frames; i++) rms += buf[i] * buf[i];
    rms = std::sqrt(rms / frames);
    // Smooth (IIR, coeff ≈ 0.95 @ 48kHz/128)
    float prev = std::pow(10.0f, out.load(std::memory_order_relaxed) * 0.05f);
    float smooth = prev * 0.95f + rms * 0.05f;
    out.store(linTodB(smooth), std::memory_order_relaxed);
}

// ─── Effects chain dispatch ───────────────────────────────────────────────────
void AudioEngine::processEffectsChain(float* buf, int32_t frames) {
    if (params_.gateEnabled.load(std::memory_order_relaxed))
        dsp_noiseGate(buf, frames);

    // Check if EQ gains changed (cheap comparison)
    for (int i = 0; i < kEqBands; i++) {
        if (params_.eqGain[i].load(std::memory_order_relaxed) != eqGainCache_[i]) {
            eqDirty_ = true;
            break;
        }
    }
    if (eqDirty_) rebuildEQ();
    if (params_.eqEnabled.load(std::memory_order_relaxed))
        dsp_eq(buf, frames);

    if (params_.compEnabled.load(std::memory_order_relaxed))
        dsp_compressor(buf, frames);

    if (params_.reverbEnabled.load(std::memory_order_relaxed))
        dsp_reverb(buf, frames);

    if (params_.pitchEnabled.load(std::memory_order_relaxed))
        dsp_pitchShift(buf, frames);
}

// ─── Noise Gate ──────────────────────────────────────────────────────────────
void AudioEngine::dsp_noiseGate(float* buf, int32_t frames) {
    float threshLin  = dBToLin(params_.gateThresholdDb.load(std::memory_order_relaxed));
    float attackCoef = 1.0f - std::exp(-1.0f / (params_.gateAttackMs.load(std::memory_order_relaxed)
                        * 0.001f * sampleRate_));
    float releaseCoef= 1.0f - std::exp(-1.0f / (params_.gateReleaseMs.load(std::memory_order_relaxed)
                        * 0.001f * sampleRate_));

    for (int i = 0; i < frames; i++) {
        // RMS with single-pole smoothing
        float x2 = buf[i] * buf[i];
        gateRms_ = gateRms_ * 0.995f + x2 * 0.005f;
        float rms = std::sqrt(gateRms_);

        float target = (rms > threshLin) ? 1.0f : 0.0f;
        float coef   = (target > gateEnv_) ? attackCoef : releaseCoef;
        gateEnv_    += coef * (target - gateEnv_);
        buf[i]      *= gateEnv_;
    }
}

// ─── 10-band EQ ──────────────────────────────────────────────────────────────
void AudioEngine::dsp_eq(float* buf, int32_t frames) {
    for (int i = 0; i < frames; i++) {
        float x = buf[i];
        for (int b = 0; b < kEqBands; b++) {
            x = eqFilters_[b].process(x);
        }
        buf[i] = x;
    }
}

// ─── Feed-forward RMS Compressor with soft knee ──────────────────────────────
void AudioEngine::dsp_compressor(float* buf, int32_t frames) {
    float threshold = params_.compThresholdDb.load(std::memory_order_relaxed);
    float ratio     = params_.compRatio.load(std::memory_order_relaxed);
    float makeup    = dBToLin(params_.compMakeupDb.load(std::memory_order_relaxed));
    float attackCoef = 1.0f - std::exp(-1.0f / (params_.compAttackMs.load(std::memory_order_relaxed)
                        * 0.001f * sampleRate_));
    float releaseCoef= 1.0f - std::exp(-1.0f / (params_.compReleaseMs.load(std::memory_order_relaxed)
                        * 0.001f * sampleRate_));
    static constexpr float kKneeWidth = 6.0f; // dB

    float totalGR = 0.0f;
    for (int i = 0; i < frames; i++) {
        // RMS envelope
        compEnv_ = compEnv_ * (1.0f - attackCoef) + (buf[i]*buf[i]) * attackCoef;
        float levelDb = linTodB(std::sqrt(compEnv_) + 1e-10f);

        // Soft-knee gain computation
        float grDb = 0.0f;
        float over = levelDb - threshold;
        if (over > kKneeWidth * 0.5f) {
            grDb = (over) * (1.0f - 1.0f/ratio);
        } else if (over > -kKneeWidth * 0.5f) {
            float t = (over + kKneeWidth * 0.5f) / kKneeWidth;
            grDb = t * t * (over) * (1.0f - 1.0f/ratio);
        }
        float targetGain = dBToLin(-grDb) * makeup;

        // Smooth gain application
        float coef = (targetGain < 1.0f) ? attackCoef : releaseCoef;
        compGainReduction_ += coef * (targetGain - compGainReduction_);
        buf[i] *= compGainReduction_;
        totalGR += grDb;
    }
    params_.gainReductionDb.store(totalGR / frames, std::memory_order_relaxed);
}

// ─── Schroeder Reverb (4 comb + 2 allpass) ───────────────────────────────────
void AudioEngine::dsp_reverb(float* buf, int32_t frames) {
    float mix     = params_.reverbMix.load(std::memory_order_relaxed);
    float room    = params_.reverbRoomSize.load(std::memory_order_relaxed);
    float damping = params_.reverbDamping.load(std::memory_order_relaxed);

    for (int b = 0; b < 4; b++) {
        combs_[b].feedback = 0.7f + room * 0.27f;
        combs_[b].damp     = damping * 0.4f;
    }

    for (int i = 0; i < frames; i++) {
        float dry = buf[i];
        float wet = 0.0f;
        for (auto& c : combs_)  wet += c.process(dry * 0.015f);
        for (auto& a : aps_)    wet  = a.process(wet);
        buf[i] = dry * (1.0f - mix) + wet * mix;
    }
}

// ─── Granular Pitch Shifter (OLA / time-stretch) ─────────────────────────────
void AudioEngine::dsp_pitchShift(float* buf, int32_t frames) {
    float semitones = params_.pitchSemitones.load(std::memory_order_relaxed);
    float rate      = std::pow(2.0f, semitones / 12.0f); // playback rate

    for (int i = 0; i < frames; i++) {
        // Write input
        pitchIn_[pitchWritePos_ % (kGrainSize*2)] = buf[i];
        pitchWritePos_++;

        // Read at variable rate (linear interpolation)
        int   ri   = (int)pitchReadFrac_;
        float frac = pitchReadFrac_ - ri;
        int   i0   = ri % (kGrainSize*2);
        int   i1   = (ri+1) % (kGrainSize*2);
        buf[i]      = pitchIn_[i0] * (1.0f - frac) + pitchIn_[i1] * frac;

        pitchReadFrac_ += rate;
        // Keep read pointer within 1 grain of write (latency = 1 grain)
        float lag = pitchWritePos_ - pitchReadFrac_;
        if (lag > kGrainSize*2 - 1) pitchReadFrac_ = pitchWritePos_ - kGrainSize;
        if (lag < 1)               pitchReadFrac_ = pitchWritePos_ - kGrainSize;
    }
}

} // namespace micplugin

// ─────────────────────────────────────────────────────────────────────────────
// JNI Bindings
// ─────────────────────────────────────────────────────────────────────────────
using namespace micplugin;

static AudioEngine* gEngine    = nullptr;
static PluginHost*  gPluginHost = nullptr;

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_micplugin_audio_OboeEngine_nativeCreate(JNIEnv*, jobject) {
    auto* engine = new AudioEngine();
    auto* host   = new PluginHost();
    engine->setPluginHost(host);
    gEngine    = engine;
    gPluginHost = host;
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT void JNICALL
Java_com_micplugin_audio_OboeEngine_nativeDestroy(JNIEnv*, jobject, jlong handle) {
    auto* engine = reinterpret_cast<AudioEngine*>(handle);
    delete engine;
    delete gPluginHost;
    gEngine     = nullptr;
    gPluginHost = nullptr;
}

JNIEXPORT jboolean JNICALL
Java_com_micplugin_audio_OboeEngine_nativeStart(JNIEnv*, jobject, jlong handle) {
    auto* engine = reinterpret_cast<AudioEngine*>(handle);
    return engine->start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_micplugin_audio_OboeEngine_nativeStop(JNIEnv*, jobject, jlong handle) {
    auto* engine = reinterpret_cast<AudioEngine*>(handle);
    engine->stop();
}

JNIEXPORT void JNICALL
Java_com_micplugin_audio_OboeEngine_nativeSetParam(
    JNIEnv*, jobject, jlong handle, jint effectId, jint paramId, jfloat value) {
    auto* engine = reinterpret_cast<AudioEngine*>(handle);
    auto& p = engine->params();
    switch (effectId) {
        case 0: // Gate
            if (paramId==0) p.gateThresholdDb.store(value, std::memory_order_relaxed);
            else if (paramId==1) p.gateAttackMs.store(value, std::memory_order_relaxed);
            else if (paramId==2) p.gateReleaseMs.store(value, std::memory_order_relaxed);
            else if (paramId==3) p.gateEnabled.store(value > 0.5f, std::memory_order_relaxed);
            break;
        case 1: // EQ
            if (paramId >= 0 && paramId < 10)
                p.eqGain[paramId].store(value, std::memory_order_relaxed);
            else if (paramId == 10) p.eqEnabled.store(value > 0.5f, std::memory_order_relaxed);
            break;
        case 2: // Compressor
            if (paramId==0) p.compThresholdDb.store(value, std::memory_order_relaxed);
            else if (paramId==1) p.compRatio.store(value, std::memory_order_relaxed);
            else if (paramId==2) p.compAttackMs.store(value, std::memory_order_relaxed);
            else if (paramId==3) p.compReleaseMs.store(value, std::memory_order_relaxed);
            else if (paramId==4) p.compMakeupDb.store(value, std::memory_order_relaxed);
            else if (paramId==5) p.compEnabled.store(value > 0.5f, std::memory_order_relaxed);
            break;
        case 3: // Reverb
            if (paramId==0) p.reverbMix.store(value, std::memory_order_relaxed);
            else if (paramId==1) p.reverbRoomSize.store(value, std::memory_order_relaxed);
            else if (paramId==2) p.reverbDamping.store(value, std::memory_order_relaxed);
            else if (paramId==3) p.reverbEnabled.store(value > 0.5f, std::memory_order_relaxed);
            break;
        case 4: // Pitch
            if (paramId==0) p.pitchSemitones.store(value, std::memory_order_relaxed);
            else if (paramId==1) p.pitchEnabled.store(value > 0.5f, std::memory_order_relaxed);
            break;
        case 98: // Monitoring (hear yourself)
            p.monitoringEnabled.store(value > 0.5f, std::memory_order_relaxed);
            break;
        case 99: // Master bypass
            p.masterBypass.store(value > 0.5f, std::memory_order_relaxed);
            break;
    }
}

JNIEXPORT jfloatArray JNICALL
Java_com_micplugin_audio_OboeEngine_nativeGetLevels(JNIEnv* env, jobject, jlong handle) {
    auto* engine = reinterpret_cast<AudioEngine*>(handle);
    auto& p = engine->params();
    float levels[3] = {
        p.inputLevelDb.load(std::memory_order_relaxed),
        p.outputLevelDb.load(std::memory_order_relaxed),
        p.gainReductionDb.load(std::memory_order_relaxed)
    };
    jfloatArray arr = env->NewFloatArray(3);
    env->SetFloatArrayRegion(arr, 0, 3, levels);
    return arr;
}

JNIEXPORT jlong JNICALL
Java_com_micplugin_audio_OboeEngine_nativeLoadPlugin(
    JNIEnv* env, jobject, jlong handle, jstring soPath, jint formatId) {
    auto* engine = reinterpret_cast<AudioEngine*>(handle);
    if (!gPluginHost) return 0;
    const char* path = env->GetStringUTFChars(soPath, nullptr);
    jlong result = gPluginHost->loadPlugin(path, formatId, engine->sampleRate());
    env->ReleaseStringUTFChars(soPath, path);
    return result;
}

JNIEXPORT void JNICALL
Java_com_micplugin_audio_OboeEngine_nativeUnloadPlugin(
    JNIEnv*, jobject, jlong handle, jlong pluginHandle) {
    if (gPluginHost) gPluginHost->unloadPlugin(pluginHandle);
}

JNIEXPORT void JNICALL
Java_com_micplugin_audio_OboeEngine_nativeSetPluginParam(
    JNIEnv*, jobject, jlong handle, jlong pluginHandle, jint paramId, jfloat value) {
    if (gPluginHost) gPluginHost->setPluginParam(pluginHandle, paramId, value);
}

JNIEXPORT jint JNICALL
Java_com_micplugin_audio_OboeEngine_nativeGetSampleRate(JNIEnv*, jobject, jlong handle) {
    return reinterpret_cast<AudioEngine*>(handle)->sampleRate();
}

JNIEXPORT jint JNICALL
Java_com_micplugin_audio_OboeEngine_nativeGetFramesPerBurst(JNIEnv*, jobject, jlong handle) {
    return reinterpret_cast<AudioEngine*>(handle)->framesPerBurst();
}

} // extern "C"

JNIEXPORT jstring JNICALL
Java_com_micplugin_audio_OboeEngine_nativeGetPluginParams(
    JNIEnv* env, jobject, jlong /*engineHandle*/, jlong pluginHandle) {
    if (!gPluginHost) return env->NewStringUTF("[]");
    auto inst = gPluginHost->findPlugin(pluginHandle);
    if (!inst) return env->NewStringUTF("[]");

    std::string json;
    switch (inst->format) {
        case PluginFormat::CLAP: json = ClapHost::getParams(inst); break;
        case PluginFormat::LV2:  json = Lv2Host::getParams(inst);  break;
        case PluginFormat::VST3: json = Vst3Bridge::getParams(inst); break;
        default: json = "[]"; break;
    }
    return env->NewStringUTF(json.c_str());
}
