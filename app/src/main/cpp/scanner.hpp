/**
 * scanner.hpp
 *
 * Utility for pattern scanning (signature scanning) in Android native libraries.
 * Helps find function addresses without relying on hardcoded offsets or symbols.
 */

#pragma once
#include <cstdint>
#include <string>
#include <vector>

namespace scanner {

    struct ModuleInfo {
        uintptr_t base;
        size_t size;
    };

    /**
     * Finds a module by name (e.g., "libcocos2dcpp.so") in /proc/self/maps
     */
    ModuleInfo get_module_info(const char* module_name);

    /**
     * Scans for a pattern of bytes within a memory range.
     * Pattern format: "E8 ? ? ? ? 48 83 C4 08" (? is wildcard)
     */
    uintptr_t find_pattern(uintptr_t start, size_t length, const char* pattern);

    /**
     * Higher level scanner that finds a pattern in a specific module.
     */
    uintptr_t find_in_module(const char* module_name, const char* pattern);

}
