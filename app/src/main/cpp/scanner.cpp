/**
 * scanner.cpp
 *
 * Implements the memory pattern scanner for Android.
 * Useful when NDK symbols are stripped in some GD versions.
 */

#include "scanner.hpp"
#include <cstdio>
#include <cstring>
#include <vector>
#include <string>
#include <dlfcn.h>
#include <sys/types.h>
#include <unistd.h>
#include <android/log.h>

#define LOG_TAG "PathfinderScanner"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace scanner {

    /**
     * Parses /proc/self/maps to find the address range of a module.
     */
    ModuleInfo get_module_info(const char* module_name) {
        FILE* fp = fopen("/proc/self/maps", "rt");
        if (!fp) return {0, 0};

        char line[512];
        uintptr_t base = 0;
        uintptr_t end = 0;
        bool found = false;

        while (fgets(line, sizeof(line), fp)) {
            if (strstr(line, module_name)) {
                if (sscanf(line, "%lx-%lx", &base, &end) == 2) {
                    if (!found) { // First occurrence is usually the base
                        found = true;
                    }
                }
            }
        }
        fclose(fp);

        if (found && base != 0) {
            return {base, (size_t)(end - base)}; // Total approximate size
        }
        return {0, 0};
    }

    /**
     * Finds a pattern of bytes in memory.
     * Pattern: "48 89 5C 24 ? 57 48 83 EC 20"
     */
    uintptr_t find_pattern(uintptr_t start, size_t length, const char* pattern) {
        if (!start || !length || !pattern) return 0;

        std::vector<int> pattern_bytes;
        const char* current = pattern;
        
        // Parse the pattern into a vector of int (-1 for wildcards)
        while (*current) {
            if (*current == '?') {
                pattern_bytes.push_back(-1);
                current++;
                if (*current == '?') current++;
            } else if (isxdigit(*current)) {
                pattern_bytes.push_back((int)strtol(current, (char**)&current, 16));
            } else {
                current++;
            }
        }

        const uint8_t* start_ptr = (const uint8_t*)start;
        const uint8_t* end_ptr = start_ptr + length - pattern_bytes.size();

        for (const uint8_t* p = start_ptr; p < end_ptr; ++p) {
            bool found = true;
            for (size_t i = 0; i < pattern_bytes.size(); ++i) {
                if (pattern_bytes[i] != -1 && p[i] != (uint8_t)pattern_bytes[i]) {
                    found = false;
                    break;
                }
            }
            if (found) return (uintptr_t)p;
        }

        return 0;
    }

    uintptr_t find_in_module(const char* module_name, const char* pattern) {
        ModuleInfo info = get_module_info(module_name);
        if (info.base == 0) {
            LOGE("Module %s not found in maps!", module_name);
            return 0;
        }
        return find_pattern(info.base, info.size, pattern);
    }

}
