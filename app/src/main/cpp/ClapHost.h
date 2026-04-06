#pragma once
#include "PluginHost.h"
#include <memory>

namespace micplugin {
    class ClapHost {
    public:
        static std::shared_ptr<PluginInstance> load(const char* path, int32_t sampleRate);
    };
}
