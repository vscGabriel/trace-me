package dev.trace.engine;

public record ProgressEvent(
        int line,
        int hole,
        int total,
        double x,
        double y,
        int progress,
        String operationType
) {}
