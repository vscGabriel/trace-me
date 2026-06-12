package dev.trace.engine;

import dev.trace.machine.MachineState;
import dev.trace.machine.MachineStateException;
import dev.trace.parser.GCodeCommand;
import dev.trace.parser.GCodeCommandType;
import dev.trace.parser.GCodeParser;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public class GCodeEngine {

    private static final Logger log = Logger.getLogger(GCodeEngine.class.getName());

    private final EngineConfig config;
    private final GCodeParser parser = new GCodeParser();

    private final AtomicReference<MachineState> state = new AtomicReference<>(MachineState.DISCONNECTED);
    private final AtomicReference<List<GCodeCommand>> commands = new AtomicReference<>(List.of());
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    private final AtomicInteger currentHole = new AtomicInteger(0);
    private final AtomicInteger totalHoles = new AtomicInteger(0);
    private final AtomicReference<CompletableFuture<Void>> pendingAck = new AtomicReference<>(null);

    public GCodeEngine(EngineConfig config) {
        this.config = config;
    }

    public void load(String rawGcode) {
        List<GCodeCommand> parsed = parser.parse(rawGcode);
        commands.set(parsed);
        totalHoles.set(parser.getHoleOperations(parsed).size());
        currentIndex.set(0);
        currentHole.set(0);
        log.info("G-code carregado: %d comandos, %d furos".formatted(parsed.size(), totalHoles.get()));
    }

    public void start() {
        transitionTo(MachineState.RUNNING);
        CompletableFuture.runAsync(this::executeNext);
    }

    public void ackReceived(int lineNumber) {
        log.info("ACK linha %d".formatted(lineNumber));
        CompletableFuture<Void> ack = pendingAck.get();
        if (ack != null) {
            ack.complete(null);
        }
    }

    public void pause() {
        transitionTo(MachineState.PAUSED);
    }

    public void resume() {
        transitionTo(MachineState.RUNNING);
        CompletableFuture.runAsync(this::executeNext);
    }

    public void stop() {
        transitionTo(MachineState.ERROR);
        CompletableFuture<Void> ack = pendingAck.getAndSet(null);
        if (ack != null) {
            ack.cancel(true);
        }
        currentIndex.set(0);
        currentHole.set(0);
    }

    public void reset() {
        transitionTo(MachineState.IDLE);
        commands.set(List.of());
        currentIndex.set(0);
        currentHole.set(0);
        totalHoles.set(0);
    }

    public MachineState getState() {
        return state.get();
    }

    private void executeNext() {
        List<GCodeCommand> cmds = commands.get();
        int idx = currentIndex.get();

        if (cmds.isEmpty() || idx >= cmds.size()) {
            finish();
            return;
        }

        GCodeCommand command = cmds.get(idx);

        if (command.isEndOfProgram()) {
            finish();
            return;
        }

        if (state.get() != MachineState.RUNNING) {
            return;
        }

        // pendingAck must be set before send() so ackReceived() never races against a null ref
        CompletableFuture<Void> ack = new CompletableFuture<>();
        pendingAck.set(ack);

        int total = cmds.size();
        int progressPct = total > 0 ? (idx * 100) / total : 0;

        String executeJson = """
                {"type":"EXECUTE","line":%d,"command":"%s"}"""
                .formatted(command.lineNumber(), command.rawLine().replace("\"", "\\\""));

        config.commandSender().send(executeJson);

        String operationType;
        if (command.isHoleOperation()) {
            operationType = "DRILL";
        } else if (command.isTrack()) {
            operationType = "TRACE";
        } else if (command.type() == GCodeCommandType.G0) {
            operationType = "RAPID";
        } else {
            operationType = "UNKNOWN";
        }

        config.progressListener().onProgress(new ProgressEvent(
                command.lineNumber(),
                currentHole.get(),
                totalHoles.get(),
                command.x() != null ? command.x() : 0.0,
                command.y() != null ? command.y() : 0.0,
                progressPct,
                operationType
        ));

        if (command.isHoleOperation()) {
            currentHole.incrementAndGet();
        }
        currentIndex.incrementAndGet();

        try {
            ack.orTimeout(config.ackTimeoutSeconds(), TimeUnit.SECONDS).get();
            executeNext();
        } catch (Exception e) {
            if (state.get() == MachineState.RUNNING) {
                log.warning("Timeout ou erro aguardando ACK da linha %d: %s"
                        .formatted(command.lineNumber(), e.getMessage()));
                transitionTo(MachineState.ERROR);
            }
        }
    }

    private void finish() {
        log.info("Execução concluída. %d furos.".formatted(currentHole.get()));
        currentIndex.set(0);
        currentHole.set(0);
        transitionTo(MachineState.FINISHED);
    }

    private void transitionTo(MachineState next) {
        MachineState current = state.get();
        if (!current.canTransitionTo(next)) {
            throw new MachineStateException(current, next);
        }
        state.set(next);
        config.stateListener().onStateChange(next);
    }
}
