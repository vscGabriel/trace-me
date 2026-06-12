package dev.trace.parser;

public class GCodeParseException extends RuntimeException {

    private final int lineNumber;
    private final String rawLine;

    public GCodeParseException(int lineNumber, String rawLine, String message) {
        super("Linha %d: %s — %s".formatted(lineNumber, rawLine, message));
        this.lineNumber = lineNumber;
        this.rawLine = rawLine;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getRawLine() {
        return rawLine;
    }
}
