#pragma once
#include "PluginHost.h"
#include <memory>
#include <string>

namespace micplugin {
    class ClapHost {
    public:
        static std::shared_ptr<PluginInstance> load(const char* path, int32_t sampleRate);
        static std::string getParams(const std::shared_ptr<PluginInstance>& inst);
    };
}
