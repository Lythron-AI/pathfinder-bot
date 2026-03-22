#include "../include/Level.hpp"
#include <iostream>
#include <string>

int main(int argc, char* argv[]) {
    if (argc != 3) {
        std::cerr << "Usage: " << argv[0] << " <level> <inputs>" << std::endl;
        return 1;
    }

    std::string lvlString = argv[1];
    std::string inputs = argv[2];

    Level level(lvlString);
    level.debug = true;

    for (char c : inputs) {
        level.runFrame(c == '1', 1.0f/240.0f);
    }

    return 0;
}

