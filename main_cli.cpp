/**
 * main_cli.cpp
 *
 * Command-line interface for the Pathfinder A* solver.
 * Perfect for Termux on Android or any Linux system.
 *
 * Usage: ./pathfinder_cli <level_string_file> <output_gdr2_file>
 */

#include <iostream>
#include <fstream>
#include <vector>
#include <string>
#include <atomic>
#include <functional>

// Our A* engine
#include <Level.hpp>

// Declared in pathfinder_android.cpp
std::vector<uint8_t> pathfind_android(
    const std::string& lvlString,
    std::atomic_bool& stop,
    std::function<void(double)> callback);

std::string readFile(const std::string& path) {
    std::ifstream file(path);
    if (!file.is_open()) return "";
    return std::string((std::istreambuf_iterator<char>(file)), std::istreambuf_iterator<char>());
}

bool writeBinary(const std::string& path, const std::vector<uint8_t>& data) {
    std::ofstream file(path, std::ios::binary);
    if (!file.is_open()) return false;
    file.write(reinterpret_cast<const char*>(data.data()), data.size());
    return true;
}

int main(int argc, char** argv) {
    if (argc < 3) {
        std::cout << "Pathfinder CLI v2.0 (Powered by gd-sim)" << std::endl;
        std::cout << "Uso: ./pathfinder_cli <archivo_lvlString> <output.gdr2>" << std::endl;
        return 1;
    }

    std::string lvlPath = argv[1];
    std::string outPath = argv[2];

    std::cout << "[*] Leyendo nivel: " << lvlPath << std::endl;
    std::string lvlString = readFile(lvlPath);
    if (lvlString.empty()) {
        std::cerr << "[!] Error: No se pudo leer el archivo o está vacío." << std::endl;
        return 2;
    }

    std::cout << "[*] Iniciando A* Nativo..." << std::endl;
    std::atomic_bool stop{false};
    
    auto result = pathfind_android(lvlString, stop, [](double progress) {
        printf("\r[*] Progreso: %.2f%%   ", progress);
        fflush(stdout);
    });

    std::cout << std::endl;

    if (result.empty()) {
        std::cerr << "[!] El motor A* no pudo encontrar una ruta válida." << std::endl;
        return 3;
    }

    if (writeBinary(outPath, result)) {
        std::cout << "[+] Replay guardado exitosamente en: " << outPath << " (" << result.size() << " bytes)" << std::endl;
    } else {
        std::cerr << "[!] Error al escribir el archivo de salida." << std::endl;
        return 4;
    }

    return 0;
}
