package dev.trace.spi;

import dev.trace.machine.MachineState;

@FunctionalInterface
public interface StateListener {
    void onStateChange(MachineState newState);
}
