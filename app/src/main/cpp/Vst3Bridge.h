#pragma once
#include "PluginHost.h"
#include <memory>

namespace micplugin {
    class Vst3Bridge {
    public:
        static std::shared_ptr<PluginInstance> load(const char* soPath, int32_t sampleRate);
    };
}
