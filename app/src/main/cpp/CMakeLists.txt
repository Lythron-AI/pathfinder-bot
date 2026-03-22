cmake_minimum_required(VERSION 3.22.1)
project(pathfinder_jni VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

# ── Dobby Hooking Library ────────────────────────────────────────────────────
FetchContent_Declare(
  dobby
  GIT_REPOSITORY https://github.com/jmpews/Dobby.git
  GIT_TAG        master
)

FetchContent_MakeAvailable(dobby)

# ── gd-sim source files ──────────────────────────────────────────────────────
set(GD_SIM_ROOT "${CMAKE_SOURCE_DIR}/../../../../../pathfinder-main/pathfinder-main/gd-sim")

file(GLOB_RECURSE GD_SIM_SOURCES
    "${GD_SIM_ROOT}/src/EffectObject.cpp"
    "${GD_SIM_ROOT}/src/Level.cpp"
    "${GD_SIM_ROOT}/src/Player.cpp"
    "${GD_SIM_ROOT}/src/Vehicle.cpp"
    "${GD_SIM_ROOT}/src/util.cpp"
    "${GD_SIM_ROOT}/src/Objects/Block.cpp"
    "${GD_SIM_ROOT}/src/Objects/BreakableBlock.cpp"
    "${GD_SIM_ROOT}/src/Objects/GravityPortal.cpp"
    "${GD_SIM_ROOT}/src/Objects/Hazard.cpp"
    "${GD_SIM_ROOT}/src/Objects/Object.cpp"
    "${GD_SIM_ROOT}/src/Objects/Orb.cpp"
    "${GD_SIM_ROOT}/src/Objects/Pad.cpp"
    "${GD_SIM_ROOT}/src/Objects/SizePortal.cpp"
    "${GD_SIM_ROOT}/src/Objects/Slope.cpp"
    "${GD_SIM_ROOT}/src/Objects/SpeedPortal.cpp"
    "${GD_SIM_ROOT}/src/Objects/VehiclePortal.cpp"
)

set(PATHFINDER_SRC_ROOT "${CMAKE_SOURCE_DIR}/../../../../../pathfinder-main/pathfinder-main/src")

# ── pathfinder_jni (App JNI) ─────────────────────────────────────────────────
add_library(pathfinder_jni SHARED
    ${GD_SIM_SOURCES}
    "${PATHFINDER_SRC_ROOT}/pathfinder_android.cpp"
    jni_bridge.cpp
    scanner.cpp
)

target_include_directories(pathfinder_jni PRIVATE
    "${GD_SIM_ROOT}/include"
)

target_compile_options(pathfinder_jni PRIVATE
    -O3 -fno-math-errno -fno-trapping-math -fvisibility=hidden -fomit-frame-pointer
)

find_library(log-lib log)
target_link_libraries(pathfinder_jni ${log-lib})

# ── pathfinder_mod (Injected library) ─────────────────────────────────────────
add_library(pathfinder_mod SHARED
    ${GD_SIM_SOURCES}
    "${PATHFINDER_SRC_ROOT}/pathfinder_android.cpp"
    injected_mod.cpp
    scanner.cpp
)

target_include_directories(pathfinder_mod PRIVATE
    "${GD_SIM_ROOT}/include"
    $<TARGET_PROPERTY:dobby,INTERFACE_INCLUDE_DIRECTORIES>
)

target_compile_definitions(pathfinder_mod PRIVATE INJECTED_MODE=1)

target_link_libraries(pathfinder_mod 
    ${log-lib} 
    android 
    dl
    dobby
)
