#include "TtlParser.h"
#include <fstream>
#include <sstream>
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "MicPlugin.TTL", __VA_ARGS__)

namespace micplugin {

std::string TtlParser::trim(const std::string& s) {
    size_t a = s.find_first_not_of(" \t\r\n");
    if (a == std::string::npos) return "";
    size_t b = s.find_last_not_of(" \t\r\n");
    return s.substr(a, b - a + 1);
}

float TtlParser::parseFloat(const std::string& s, float fallback) {
    try {
        std::string clean = s;
        while (!clean.empty() && (clean.back() == ',' || clean.back() == ';' ||
                                   clean.back() == ' ' || clean.back() == '.'))
            clean.pop_back();
        return std::stof(clean);
    } catch (...) { return fallback; }
}

std::vector<LV2PortInfo> TtlParser::parsePorts(const std::string& ttl) {
    std::vector<LV2PortInfo> ports;

    std::istringstream ss(ttl);
    std::string line;

    bool inPort    = false;
    LV2PortInfo cur{};

    while (std::getline(ss, line)) {
        std::string t = trim(line);
        if (t.empty() || t[0] == '#') continue;

        // Port block starts with '[' alone (possibly with trailing comma before)
        if (t == "[") {
            if (inPort && cur.index >= 0) ports.push_back(cur);
            cur   = LV2PortInfo{};
            inPort = true;
            continue;
        }

        // Port block ends with ']' (possibly '] ,' or '] .')
        if (!t.empty() && t[0] == ']') {
            if (inPort && cur.index >= 0) ports.push_back(cur);
            cur    = LV2PortInfo{};
            inPort = false;
            continue;
        }

        if (!inPort) continue;

        // Type line: "a lv2:InputPort , lv2:ControlPort ;"
        if (t.find("a lv2:") == 0 || t.find("a  lv2:") == 0) {
            if (t.find("lv2:ControlPort") != std::string::npos) cur.isControl = true;
            if (t.find("lv2:InputPort")   != std::string::npos) cur.isInput   = true;
            // AudioPort → not control
            if (t.find("lv2:AudioPort")   != std::string::npos) cur.isControl = false;
            continue;
        }

        // Extract value after the predicate
        auto extractVal = [&](const std::string& pred) -> std::string {
            size_t p = t.find(pred);
            if (p == std::string::npos) return "";
            std::string rest = trim(t.substr(p + pred.size()));
            while (!rest.empty() && (rest.back() == ';' || rest.back() == ',' ||
                                      rest.back() == ' ' || rest.back() == '.'))
                rest.pop_back();
            return trim(rest);
        };

        if (t.find("lv2:index") != std::string::npos) {
            auto v = extractVal("lv2:index");
            try { cur.index = std::stoi(v); } catch (...) {}
        }
        if (t.find("lv2:name") != std::string::npos && cur.name.empty()) {
            auto v = extractVal("lv2:name");
            if (v.size() >= 2 && v.front() == '"') v = v.substr(1, v.size() - 2);
            cur.name = v;
        }
        if (t.find("lv2:minimum") != std::string::npos)
            cur.minimum    = parseFloat(extractVal("lv2:minimum"), 0.f);
        if (t.find("lv2:maximum") != std::string::npos)
            cur.maximum    = parseFloat(extractVal("lv2:maximum"), 1.f);
        if (t.find("lv2:default") != std::string::npos)
            cur.defaultVal = parseFloat(extractVal("lv2:default"), 0.f);
    }

    LOGI("TTL parse: %zu ports", ports.size());
    return ports;
}

std::vector<LV2PortInfo> TtlParser::parseFromSo(const std::string& soPath) {
    auto dir  = soPath.substr(0, soPath.find_last_of("/\\") + 1);
    auto base = soPath.substr(soPath.find_last_of("/\\") + 1);
    auto stem = base.substr(0, base.find_last_of('.'));

    std::vector<std::string> candidates = {
        dir + stem + ".ttl",
        dir + "manifest.ttl",
        dir + stem + ".lv2/manifest.ttl",
        dir + "../manifest.ttl",
    };

    for (auto& path : candidates) {
        std::ifstream f(path);
        if (!f.good()) continue;
        LOGI("Found TTL: %s", path.c_str());
        std::string content((std::istreambuf_iterator<char>(f)),
                             std::istreambuf_iterator<char>());
        auto ports = parsePorts(content);
        if (!ports.empty()) return ports;
    }
    LOGI("No TTL found for: %s", soPath.c_str());
    return {};
}

} // namespace micplugin
