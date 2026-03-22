cmake_minimum_required(VERSION 3.24.0 FATAL_ERROR)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

project(gd-sim VERSION 0.1.0 LANGUAGES CXX)

file(GLOB_RECURSE SOURCES CONFIGURE_DEPENDS src/*.cpp)

add_library(gd-sim STATIC ${SOURCES})

target_include_directories(gd-sim PUBLIC include)

add_executable(gd-sim-test test/test.cpp)

target_link_libraries(gd-sim-test gd-sim)

target_include_directories(gd-sim-test PRIVATE include)
