#pragma once
#include "../base/ipluginbase.h"
#include "vsttypes.h"
namespace Steinberg { namespace Vst {
    struct IComponent : FUnknown {
        static const TUID iid;
        virtual tresult PLUGIN_API initialize(FUnknown* context) = 0;
        virtual tresult PLUGIN_API terminate() = 0;
        virtual tresult PLUGIN_API getControllerClassId(TUID classId) = 0;
        virtual tresult PLUGIN_API setActive(uint8 state) = 0;
        virtual tresult PLUGIN_API setIoMode(int32 mode) = 0;
        virtual int32   PLUGIN_API getBusCount(MediaType type, BusDirection dir) = 0;
        virtual tresult PLUGIN_API getBusInfo(MediaType type, BusDirection dir, int32 idx, void* info) = 0;
        virtual tresult PLUGIN_API getRoutingInfo(void* inInfo, void* outInfo) = 0;
        virtual tresult PLUGIN_API activateBus(MediaType type, BusDirection dir, int32 idx, uint8 state) = 0;
    };
}}
