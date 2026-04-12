// Definitions for all VST3 interface IIDs used in MicUp
#include "vst/ivstcomponent.h"
#include "vst/ivstaudioprocessor.h"
#include "vst/ivsteditcontroller.h"
#include "base/ipluginbase.h"

namespace Steinberg {
    const TUID FUnknown::iid        = {0x00000000,0x00000000,0xC0000000,0x00000046};
    const TUID IPluginFactory::iid  = {0x7A4D811C,0x52114A1F,0xAED9D2EE,0x0B43BF9F};

    namespace Vst {
        const TUID IComponent::iid        = {0xE831FF31,0xF2D54301,0x928EBBEE,0x25697802};
        const TUID IAudioProcessor::iid   = {0x42043F99,0xB7DA453C,0xA569D79C,0xFCDE215A};
        const TUID IEditController::iid   = {0xDCD7BBE3,0x7742448D,0xA874AACC,0x979C9D2B};
    }
}
