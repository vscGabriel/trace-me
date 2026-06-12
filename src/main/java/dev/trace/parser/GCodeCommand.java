package dev.trace.parser;

public record GCodeCommand(
        int lineNumber,
        GCodeCommandType type,
        Double x,
        Double y,
        Double z,
        Double feedRate,
        Double spindleSpeed,
        String rawLine
) {
    public boolean isHoleOperation() {
        return type == GCodeCommandType.G1 && z != null && z < 0;
    }

    public boolean isEndOfProgram() {
        return type == GCodeCommandType.M2 || type == GCodeCommandType.M30;
    }
}
