package dev.trace.parser;

public enum GCodeCommandType {
    G0,      // movimento rápido (sem corte)
    G1,      // movimento linear (trilha/fresagem)
    G20,     // unidade: polegadas
    G21,     // unidade: milímetros
    G80,     // cancelar ciclo enlatado
    G81,     // ciclo enlatado de furação (drilling canned cycle)
    G90,     // posicionamento absoluto
    G98,     // retorno ao plano inicial entre furos
    M0,      // pausa programada (troca de ferramenta) — também M00
    M3,      // ligar fuso — também M03
    M5,      // desligar fuso — também M05
    M2,      // fim do programa
    M30,     // fim do programa + rebobinar
    UNKNOWN  // comando não suportado
}
