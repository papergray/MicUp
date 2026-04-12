#pragma once
#include "ivstcomponent.h"
namespace Steinberg { namespace Vst {
    struct IEditController : FUnknown {
        static const TUID iid;
        virtual tresult PLUGIN_API initialize(FUnknown* context) = 0;
        virtual tresult PLUGIN_API terminate() = 0;
        virtual tresult PLUGIN_API setComponentState(void* state) = 0;
        virtual tresult PLUGIN_API setState(void* state) = 0;
        virtual tresult PLUGIN_API getState(void* state) = 0;
        virtual int32   PLUGIN_API getParameterCount() = 0;
        virtual tresult PLUGIN_API getParameterInfo(int32 paramIndex, ParameterInfo& info) = 0;
        virtual tresult PLUGIN_API getParamStringByValue(ParamID id, ParamValue valueNormalized, void* string) = 0;
        virtual tresult PLUGIN_API getParamValueByString(ParamID id, void* string, ParamValue& valueNormalized) = 0;
        virtual ParamValue PLUGIN_API normalizedParamToPlain(ParamID id, ParamValue valueNormalized) = 0;
        virtual ParamValue PLUGIN_API plainParamToNormalized(ParamID id, ParamValue plainValue) = 0;
        virtual ParamValue PLUGIN_API getParamNormalized(ParamID id) = 0;
        virtual tresult PLUGIN_API setParamNormalized(ParamID id, ParamValue value) = 0;
        virtual tresult PLUGIN_API setComponentHandler(void* handler) = 0;
        virtual void*   PLUGIN_API createView(void* name) = 0;
    };
}}
