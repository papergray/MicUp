#pragma once
#include "../base/ftypes.h"
namespace Steinberg { namespace Vst {
    typedef uint32   ParamID;
    typedef double   ParamValue;
    typedef double   SampleRate;
    typedef uint32   SpeakerArrangement;
    typedef int32    MediaType;
    typedef int32    BusDirection;
    typedef int32    BusType;
    static const MediaType    kAudio = 0;
    static const BusDirection kInput = 0, kOutput = 1;
    static const char8* kVstAudioEffectClass = "Audio Module Class";
    enum SymbolicSampleSizes { kSample32 = 0, kSample64 = 1 };
    enum ProcessModes { kRealtime = 0, kPrefetch = 1, kOffline = 2 };

    struct ParamInfo {
        ParamID   id;
        char16    title[128];
        char16    shortTitle[128];
        char16    units[128];
        int32     stepCount;
        ParamValue defaultNormalizedValue;
        int32     unitId;
        int32     flags;
    };
    typedef ParamInfo ParameterInfo;

    struct AudioBusBuffers {
        int32   numChannels;
        uint64  silenceFlags;
        union { float** channelBuffers32; double** channelBuffers64; };
    };

    struct ProcessSetup {
        int32      processMode;
        int32      symbolicSampleSize;
        int32      maxSamplesPerBlock;
        SampleRate sampleRate;
    };

    struct ProcessData {
        int32            processMode;
        int32            symbolicSampleSize;
        int32            numSamples;
        int32            numInputs;
        int32            numOutputs;
        AudioBusBuffers* inputs;
        AudioBusBuffers* outputs;
        void*            inputParameterChanges;
        void*            outputParameterChanges;
        void*            inputEvents;
        void*            outputEvents;
        void*            processContext;
    };
}}
