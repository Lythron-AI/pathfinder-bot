/**
 * injected_mod.cpp - ULTRA MODE (FIXED)
 */

#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <string>
#include <vector>
#include <atomic>
#include <thread>
#include <functional>
#include <mutex>
#include <algorithm>
#include "scanner.hpp"
#include "dobby.h"

#define LOG_TAG "PathfinderUltra"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// ── Patterns (2.2) ──────────────────────────────────────────────────────────
#define PAT_PLAYLAYER_INIT     "FF 03 01 D1 F8 5F 02 A9 F6 57 03 A9 F4 4F 04 A9"
#define PAT_PLAYLAYER_UPDATE   "FF 83 01 D1 F4 4F 02 A9 FD 7B 03 A9 FD 43 01 91"
#define PAT_PLAYER_PUSH        "FF 43 01 D1 F8 5F 02 A9 FD 7B 03 A9 FD 43 01 91"
#define PAT_PLAYER_RELEASE     "FF 03 01 D1 F8 5F 02 A9 F6 57 03 A9 F4 4F 04 A9"

// ── State ────────────────────────────────────────────────────────────────────
static std::vector<bool> g_timeline;
static std::mutex g_mod_mutex;
static std::atomic_bool g_ultra_active{false};
static std::atomic<int> g_frame{0};

static bool (*PlayLayer_init_orig)(void*, void*, bool, bool);
static void (*PlayLayer_update_orig)(void*, float);
static void (*Player_push_orig)(void*, int);
static void (*Player_release_orig)(void*, int);

std::vector<uint8_t> pathfind_android(const std::string& lvlString, std::atomic_bool& stop, std::function<void(double)> callback);

// ── Helper ───────────────────────────────────────────────────────────────────
void load_gdr2_to_timeline(const std::vector<uint8_t>& gdr2) {
    if (gdr2.size() < 16) return;
    
    auto read32 = [&](size_t off) -> uint32_t {
        return (uint32_t)gdr2[off] | ((uint32_t)gdr2[off+1] << 8) | ((uint32_t)gdr2[off+2] << 16) | ((uint32_t)gdr2[off+3] << 24);
    };

    uint32_t fps = read32(8);
    uint32_t count = read32(12);
    
    std::lock_guard<std::mutex> lock(g_mod_mutex);
    g_timeline.clear();
    
    struct Input { uint32_t frame; bool down; };
    std::vector<Input> raw_inputs;
    size_t offset = 16;
    for (uint32_t i = 0; i < count; ++i) {
        if (offset + 7 > gdr2.size()) break;
        uint32_t f = read32(offset);
        bool down = gdr2[offset + 6] != 0;
        raw_inputs.push_back({f, down});
        offset += 7;
    }

    if (raw_inputs.empty()) return;
    
    uint32_t lastFrame = raw_inputs.back().frame;
    g_timeline.assign(lastFrame + 1, false);
    
    bool state = false;
    uint32_t cursor = 0;
    for (uint32_t f = 0; f <= lastFrame; ++f) {
        if (cursor < raw_inputs.size() && raw_inputs[cursor].frame == f) {
            state = raw_inputs[cursor].down;
            cursor++;
        }
        g_timeline[f] = state;
    }
}

// ── Hooks ────────────────────────────────────────────────────────────────────

bool PlayLayer_init_hook(void* self, void* level, bool p1, bool p2) {
    LOGI("PlayLayer::init hook");
    {
        std::lock_guard<std::mutex> lock(g_mod_mutex);
        g_timeline.clear();
        g_frame = 0;
        g_ultra_active = false;
    }
    return PlayLayer_init_orig(self, level, p1, p2);
}

void PlayLayer_update_hook(void* self, float dt) {
    PlayLayer_update_orig(self, dt);

    if (g_ultra_active) {
        int f = g_frame.load();
        bool pressed = false;
        {
            std::lock_guard<std::mutex> lock(g_mod_mutex);
            if (f < (int)g_timeline.size()) pressed = g_timeline[f];
        }
        void* p1 = *(void**)((uintptr_t)self + 0x8c0); 
        if (p1 && Player_push_orig && Player_release_orig) {
            if (pressed) Player_push_orig(p1, 1);
            else Player_release_orig(p1, 1);
        }
        g_frame++;
    }
}

// ── JNI Exports ──────────────────────────────────────────────────────────────

extern "C" {
    JNIEXPORT void JNICALL
    Java_com_pathfinder_gd_PathfinderEngine_injectUltra(JNIEnv* env, jclass clazz) {
        void* handle = dlopen("libcocos2dcpp.so", RTLD_NOW);
        const char* lib = "libcocos2dcpp.so";

        auto resolve = [&](const char* name, const char* pat) -> void* {
            void* addr = handle ? dlsym(handle, name) : nullptr;
            if (!addr) addr = (void*)scanner::find_in_module(lib, pat);
            return addr;
        };

        void* pl_init = resolve("_ZN9PlayLayer4initEP11GJGameLevelbb", PAT_PLAYLAYER_INIT);
        void* pl_upd  = resolve("_ZN9PlayLayer6updateEf",             PAT_PLAYLAYER_UPDATE);
        void* p_push  = resolve("_ZN12PlayerObject10pushButtonEi",   PAT_PLAYER_PUSH);
        void* p_rel   = resolve("_ZN12PlayerObject13releaseButtonEi",  PAT_PLAYER_RELEASE);

        if (pl_init) DobbyHook(pl_init, (void*)PlayLayer_init_hook, (void**)&PlayLayer_init_orig);
        if (pl_upd)  DobbyHook(pl_upd,  (void*)PlayLayer_update_hook, (void**)&PlayLayer_update_orig);
        
        Player_push_orig    = (void (*)(void*, int))p_push;
        Player_release_orig = (void (*)(void*, int))p_rel;
        LOGI("Modo Ultra Inyectado.");
    }
}
