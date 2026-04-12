#include "TtlParser.h"
#include <fstream>
#include <sstream>
#include <algorithm>
#include <cctype>
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
        // Strip trailing punctuation like , or ;
        std::string clean = s;
        while (!clean.empty() && (clean.back() == ',' || clean.back() == ';' || clean.back() == ' '))
            clean.pop_back();
        return std::stof(clean);
    } catch (...) { return fallback; }
}

std::vector<LV2PortInfo> TtlParser::parsePorts(const std::string& ttl) {
    std::vector<LV2PortInfo> ports;
    LV2PortInfo current;
    bool inPort = false;

    std::istringstream ss(ttl);
    std::string line;

    while (std::getline(ss, line)) {
        std::string t = trim(line);
        if (t.empty() || t[0] == '#') continue;

        // Start of a port block
        if (t.find("lv2:Port") != std::string::npos ||
            t.find("lv2:ControlPort") != std::string::npos ||
            t.find("lv2:InputPort") != std::string::npos ||
            t.find("lv2:OutputPort") != std::string::npos) {
            if (inPort && current.index >= 0) ports.push_back(current);
            current = LV2PortInfo{};
            inPort  = true;
        }

        if (!inPort) continue;

        // Port index
        auto extractVal = [&](const std::string& key) -> std::string {
            size_t p = t.find(key);
            if (p == std::string::npos) return "";
            std::string rest = trim(t.substr(p + key.size()));
            // Remove trailing ; or ,
            while (!rest.empty() && (rest.back() == ';' || rest.back() == ',' || rest.back() == ' '))
                rest.pop_back();
            return rest;
        };

        if (t.find("lv2:index") != std::string::npos) {
            auto v = extractVal("lv2:index");
            try { current.index = std::stoi(v); } catch (...) {}
        }
        if (t.find("lv2:name") != std::string::npos ||
            t.find("lv2:symbol") != std::string::npos) {
            auto v = extractVal("lv2:name");
            if (v.empty()) v = extractVal("lv2:symbol");
            // Strip surrounding quotes
            if (v.size() >= 2 && v.front() == '"') v = v.substr(1, v.size() - 2);
            if (!v.empty() && current.name.empty()) current.name = v;
        }
        if (t.find("lv2:minimum") != std::string::npos)
            current.minimum = parseFloat(extractVal("lv2:minimum"), 0.f);
        if (t.find("lv2:maximum") != std::string::npos)
            current.maximum = parseFloat(extractVal("lv2:maximum"), 1.f);
        if (t.find("lv2:default") != std::string::npos)
            current.defaultVal = parseFloat(extractVal("lv2:default"), 0.f);

        if (t.find("lv2:ControlPort") != std::string::npos) current.isControl = true;
        if (t.find("lv2:InputPort")   != std::string::npos) current.isInput   = true;

        // Block end
        if (t.find("] ;") != std::string::npos || t == "]" || t == "] .") {
            if (inPort && current.index >= 0) {
                ports.push_back(current);
                current  = LV2PortInfo{};
                inPort   = false;
            }
        }
    }
    if (inPort && current.index >= 0) ports.push_back(current);

    LOGI("TTL parse: %zu ports found", ports.size());
    return ports;
}

std::vector<LV2PortInfo> TtlParser::parseFromSo(const std::string& soPath) {
    // Try <name>.ttl, manifest.ttl in same dir
    auto dir = soPath.substr(0, soPath.find_last_of("/\\") + 1);
    auto base = soPath.substr(soPath.find_last_of("/\\") + 1);
    // Remove extension
    auto stem = base.substr(0, base.find_last_of('.'));

    std::vector<std::string> candidates = {
        dir + stem + ".ttl",
        dir + "manifest.ttl",
        dir + stem + "/" + "manifest.ttl",
        dir + stem + ".lv2/manifest.ttl",
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
