#include "Vst3Bridge.h"
#include <dlfcn.h>
#include <android/log.h>
#include <cstring>
#include <string>
#include <vector>
#include <sstream>

// Steinberg VST3 SDK headers (via FetchContent)
#include "vst3/vst/ivstaudioprocessor.h"
#include "vst3/vst/ivsteditcontroller.h"
#include "vst3/base/ipluginbase.h"
#include "vst3/vst/ivstcomponent.h"

#define LOG_TAG "MicPlugin.VST3"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace Steinberg;
using namespace Steinberg::Vst;


namespace micplugin {

// ─── Minimal host application (required by SDK) ───────────────────────────────
class Vst3HostApp : public FUnknown {
public:
    tresult PLUGIN_API queryInterface(const TUID iid, void** obj) override {
        *obj = nullptr; return kNoInterface;
    }
    uint32 PLUGIN_API addRef()  override { return 1; }
    uint32 PLUGIN_API release() override { return 1; }
};
static Vst3HostApp gHostApp;

struct Vst3PluginInstance : public PluginInstance {
    void*               dlib        = nullptr;
    IComponent*         component   = nullptr;
    IAudioProcessor*    processor   = nullptr;
    IEditController*    controller  = nullptr;

    // Cached param info
    struct ParamEntry { ParamID id; std::string name; float min,max,def; };
    std::vector<ParamEntry> params;

    // Audio buffers
    std::vector<float> inputBuf, outputBuf;
    float* inPtr[1]  = {};
    float* outPtr[1] = {};

    bool process(float* buf, int32_t frames, int32_t sr) override {
        if (!processor) return true; // pass-through
        if ((int32_t)inputBuf.size() < frames) {
            inputBuf.resize(frames); outputBuf.resize(frames, 0.f);
            inPtr[0] = inputBuf.data(); outPtr[0] = outputBuf.data();
        }
        std::memcpy(inputBuf.data(), buf, frames * sizeof(float));

        AudioBusBuffers inBus{}, outBus{};
        inBus.numChannels  = 1; inBus.channelBuffers32  = inPtr;
        outBus.numChannels = 1; outBus.channelBuffers32 = outPtr;

        ProcessData data{};
        data.numSamples          = frames;
        data.symbolicSampleSize  = kSample32;
        data.numInputs           = 1; data.inputs  = &inBus;
        data.numOutputs          = 1; data.outputs = &outBus;

        if (processor->process(data) == kResultOk)
            std::memcpy(buf, outputBuf.data(), frames * sizeof(float));
        return true;
    }

    void setParam(int32_t id, float value) override {
        if (!controller) return;
        controller->setParamNormalized((ParamID)id, (ParamValue)value);
    }

    void cleanup() override {
        if (processor) { processor->setProcessing(false); processor->release(); processor = nullptr; }
        if (component) { component->setActive(false); component->terminate(); component->release(); component = nullptr; }
        if (controller){ controller->terminate(); controller->release(); controller = nullptr; }
        if (dlib)      { dlclose(dlib); dlib = nullptr; }
    }
};

using GetPluginFactory_fn = IPluginFactory*(*)();

std::shared_ptr<PluginInstance> Vst3Bridge::load(const char* soPath, int32_t sampleRate) {
    void* dlib = dlopen(soPath, RTLD_NOW | RTLD_LOCAL);
    if (!dlib) { LOGE("dlopen failed: %s — %s", soPath, dlerror()); return nullptr; }

    auto* getFactory = reinterpret_cast<GetPluginFactory_fn>(dlsym(dlib, "GetPluginFactory"));
    if (!getFactory) { LOGE("GetPluginFactory not found"); dlclose(dlib); return nullptr; }

    IPluginFactory* factory = getFactory();
    if (!factory)   { LOGE("factory null"); dlclose(dlib); return nullptr; }

    int32 count = factory->countClasses();
    LOGI("VST3 factory: %d classes in %s", count, soPath);

    // Find first audio effect class
    PClassInfo ci;
    int targetIdx = -1;
    for (int32 i = 0; i < count; i++) {
        if (factory->getClassInfo(i, &ci) == kResultOk) {
            if (strcmp(ci.category, kVstAudioEffectClass) == 0) {
                targetIdx = i; break;
            }
        }
    }
    if (targetIdx < 0) { LOGE("No audio effect class found"); dlclose(dlib); return nullptr; }

    // Create component
    IComponent* comp = nullptr;
    if (factory->createInstance(ci.cid, IComponent::iid, (void**)&comp) != kResultOk || !comp) {
        LOGE("createInstance failed"); dlclose(dlib); return nullptr;
    }
    if (comp->initialize(&gHostApp) != kResultOk) {
        LOGE("component initialize failed"); comp->release(); dlclose(dlib); return nullptr;
    }

    // Get audio processor
    IAudioProcessor* proc = nullptr;
    comp->queryInterface(IAudioProcessor::iid, (void**)&proc);

    // Setup processing
    if (proc) {
        ProcessSetup setup{};
        setup.processMode        = kRealtime;
        setup.symbolicSampleSize = kSample32;
        setup.maxSamplesPerBlock = 4096;
        setup.sampleRate         = (SampleRate)sampleRate;
        proc->setupProcessing(setup);
        comp->setActive(true);
        proc->setProcessing(true);
    }

    // Get edit controller for params
    IEditController* ctrl = nullptr;
    comp->queryInterface(IEditController::iid, (void**)&ctrl);
    if (!ctrl) {
        // Try separate controller class
        TUID ctrlCid;
        if (comp->getControllerClassId(ctrlCid) == kResultOk) {
            factory->createInstance(ctrlCid, IEditController::iid, (void**)&ctrl);
            if (ctrl) ctrl->initialize(&gHostApp);
        }
    }

    auto instance        = std::make_shared<Vst3PluginInstance>();
    instance->dlib       = dlib;
    instance->component  = comp;
    instance->processor  = proc;
    instance->controller = ctrl;
    instance->format     = PluginFormat::VST3;
    instance->path       = soPath;
    instance->inputBuf.resize(4096); instance->outputBuf.resize(4096);
    instance->inPtr[0]   = instance->inputBuf.data();
    instance->outPtr[0]  = instance->outputBuf.data();

    // Cache params
    if (ctrl) {
        int32 pcount = ctrl->getParameterCount();
        LOGI("VST3 params: %d", pcount);
        for (int32 i = 0; i < pcount; i++) {
            ParameterInfo pi{};
            if (ctrl->getParameterInfo(i, pi) == kResultOk) {
                Vst3PluginInstance::ParamEntry e;
                e.id  = pi.id;
                // Convert UTF-16 name to UTF-8 (simplified: take low byte)
                for (int j = 0; pi.title[j] && j < 128; j++)
                    e.name += (char)(pi.title[j] & 0xFF);
                e.min = 0.f; e.max = 1.f;
                e.def = (float)pi.defaultNormalizedValue;
                instance->params.push_back(e);
            }
        }
    }

    LOGI("VST3 loaded: %s (%zu params)", soPath, instance->params.size());
    return instance;
}

std::string Vst3Bridge::getParams(const std::shared_ptr<PluginInstance>& inst) {
    auto* vi = static_cast<Vst3PluginInstance*>(inst.get());
    if (!vi || vi->params.empty()) return "[]";

    std::string json = "[";
    for (size_t i = 0; i < vi->params.size(); i++) {
        auto& p = vi->params[i];
        if (i > 0) json += ",";
        json += "{\"id\":"     + std::to_string(p.id)  + ",";
        json += "\"name\":\""  + p.name                + "\",";
        json += "\"min\":"     + std::to_string(p.min) + ",";
        json += "\"max\":"     + std::to_string(p.max) + ",";
        json += "\"default\":" + std::to_string(p.def) + "}";
    }
    json += "]";
    return json;
}

} // namespace micplugin
