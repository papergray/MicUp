#pragma once
#include "ftypes.h"
namespace Steinberg {
    static const int32 kResultOk        = 0;
    static const int32 kResultTrue      = kResultOk;
    static const int32 kResultFalse     = 1;
    static const int32 kNoInterface     = 2;
    static const int32 kInvalidArgument = 3;
    static const int32 kNotImplemented  = 4;
    static const int32 kInternalError   = 5;
    typedef int32 tresult;
    typedef TUID  FUID;

    struct FUnknown {
        static const TUID iid;
        virtual tresult PLUGIN_API queryInterface(const TUID iid, void** obj) = 0;
        virtual uint32  PLUGIN_API addRef()  = 0;
        virtual uint32  PLUGIN_API release() = 0;
        virtual ~FUnknown() = default;
    };

    struct IPluginFactory : FUnknown {
        static const TUID iid;
        virtual tresult PLUGIN_API getFactoryInfo(void* info) = 0;
        virtual int32   PLUGIN_API countClasses() = 0;
        virtual tresult PLUGIN_API getClassInfo(int32 idx, void* info) = 0;
        virtual tresult PLUGIN_API createInstance(const char8* cid, const char8* iid, void** obj) = 0;
    };

    struct PClassInfo {
        char8  cid[16];
        int32  cardinality;
        char8  category[32];
        char8  name[64];
    };
}
