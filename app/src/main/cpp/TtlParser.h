#pragma once
#include <string>
#include <vector>

namespace micplugin {

struct LV2PortInfo {
    int         index   = -1;
    std::string name;
    float       minimum = 0.f;
    float       maximum = 1.f;
    float       defaultVal = 0.f;
    bool        isControl  = false;
    bool        isInput    = false;
};

/**
 * Minimal Turtle/TTL parser — extracts LV2 port metadata only.
 * Handles the subset written by most LV2 plugin generators.
 * Not a full RDF parser — regex-style line scanning.
 */
class TtlParser {
public:
    /** Parse manifest.ttl or plugin .ttl from a string. Returns port list. */
    static std::vector<LV2PortInfo> parsePorts(const std::string& ttl);

    /** Load .ttl file next to soPath and parse it. */
    static std::vector<LV2PortInfo> parseFromSo(const std::string& soPath);

private:
    static std::string trim(const std::string& s);
    static float       parseFloat(const std::string& s, float fallback);
};

} // namespace micplugin
