package dev.trace.machine;

public enum MachineState {
    DISCONNECTED,
    IDLE,
    RUNNING,
    PAUSED,
    FINISHED,
    ERROR;

    public boolean canTransitionTo(MachineState next) {
        return switch (this) {
            case DISCONNECTED -> next == IDLE;
            case IDLE         -> next == RUNNING || next == DISCONNECTED;
            case RUNNING      -> next == PAUSED || next == FINISHED || next == ERROR || next == DISCONNECTED;
            case PAUSED       -> next == RUNNING || next == ERROR || next == DISCONNECTED;
            case FINISHED     -> next == IDLE || next == DISCONNECTED;
            case ERROR        -> next == IDLE || next == DISCONNECTED;
        };
    }
}
