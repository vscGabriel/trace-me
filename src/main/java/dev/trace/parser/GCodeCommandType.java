package dev.trace.parser;

public enum GCodeCommandType {
    G0,      // movimento rápido (sem corte)
    G1,      // movimento linear (corte/furo)
    G20,     // unidade: polegadas
    G21,     // unidade: milímetros
    M2,      // fim do programa
    M30,     // fim do programa + rebobinar
    UNKNOWN  // comando não suportado
}
