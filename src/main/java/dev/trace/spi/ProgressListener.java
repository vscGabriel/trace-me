package dev.trace.spi;

import dev.trace.engine.ProgressEvent;

@FunctionalInterface
public interface ProgressListener {
    void onProgress(ProgressEvent event);
}
