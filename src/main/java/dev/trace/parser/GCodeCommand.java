package dev.trace.parser;

public record GCodeCommand(
        int lineNumber,
        GCodeCommandType type,
        Double x,
        Double y,
        Double z,
        Double r,
        Double feedRate,
        Double spindleSpeed,
        String rawLine
) {
    /** Furo: qualquer comando dentro do bloco G81/G80, independente do Z. */
    public boolean isHoleOperation() {
        return type == GCodeCommandType.G81;
    }

    /** Trilha: movimento linear de fresagem/gravação. */
    public boolean isTrack() {
        return type == GCodeCommandType.G1;
    }

    public boolean isEndOfProgram() {
        return type == GCodeCommandType.M2 || type == GCodeCommandType.M30;
    }
}
