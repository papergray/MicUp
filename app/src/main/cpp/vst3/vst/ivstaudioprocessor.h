#pragma once
#include "ivstcomponent.h"
namespace Steinberg { namespace Vst {
    struct IAudioProcessor : FUnknown {
        static const TUID iid;
        virtual tresult PLUGIN_API setBusArrangements(SpeakerArrangement* inputs, int32 numIns,
                                                       SpeakerArrangement* outputs, int32 numOuts) = 0;
        virtual tresult PLUGIN_API getBusArrangement(BusDirection dir, int32 idx, SpeakerArrangement& arr) = 0;
        virtual tresult PLUGIN_API canProcessSampleSize(int32 symbolicSampleSize) = 0;
        virtual uint32  PLUGIN_API getLatencySamples() = 0;
        virtual tresult PLUGIN_API setupProcessing(ProcessSetup& setup) = 0;
        virtual tresult PLUGIN_API setProcessing(uint8 state) = 0;
        virtual tresult PLUGIN_API process(ProcessData& data) = 0;
        virtual uint32  PLUGIN_API getTailSamples() = 0;
    };
}}
