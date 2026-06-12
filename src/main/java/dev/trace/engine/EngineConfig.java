package dev.trace.engine;

import dev.trace.spi.CommandSender;
import dev.trace.spi.ProgressListener;
import dev.trace.spi.StateListener;

public record EngineConfig(
        int ackTimeoutSeconds,
        CommandSender commandSender,
        StateListener stateListener,
        ProgressListener progressListener
) {
    public static EngineConfig of(
            CommandSender sender,
            StateListener stateListener,
            ProgressListener progressListener
    ) {
        return new EngineConfig(30, sender, stateListener, progressListener);
    }
}
