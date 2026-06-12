package dev.trace.machine;

public class MachineStateException extends RuntimeException {

    public MachineStateException(MachineState current, MachineState attempted) {
        super("Transição inválida: %s → %s".formatted(current, attempted));
    }
}
