package dev.trace.engine;

import dev.trace.machine.MachineState;
import dev.trace.machine.MachineStateException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GCodeEngineTest {

    private static final Pattern LINE_PATTERN = Pattern.compile("\"line\":(\\d+)");

    private int extractLine(String json) {
        Matcher m = LINE_PATTERN.matcher(json);
        if (m.find()) return Integer.parseInt(m.group(1));
        throw new IllegalArgumentException("No 'line' field in: " + json);
    }

    private record TestContext(
            GCodeEngine engine,
            BlockingQueue<String> commandQueue,
            List<MachineState> stateChanges
    ) {}

    private TestContext createEngine() {
        BlockingQueue<String> commandQueue = new LinkedBlockingQueue<>();
        List<MachineState> stateChanges = new CopyOnWriteArrayList<>();
        EngineConfig config = new EngineConfig(5, commandQueue::add, stateChanges::add, event -> {});
        GCodeEngine engine = new GCodeEngine(config);
        return new TestContext(engine, commandQueue, stateChanges);
    }

    private void waitForState(GCodeEngine engine, MachineState expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (engine.getState() != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertThat(engine.getState()).isEqualTo(expected);
    }

    @Test
    void loadAndStartWithManualAck() throws Exception {
        TestContext ctx = createEngine();
        GCodeEngine engine = ctx.engine();

        engine.reset(); // DISCONNECTED -> IDLE
        engine.load("G21\nG1 Z-1.5 F100\nM30");
        engine.start();  // IDLE -> RUNNING

        // G21 (line 1)
        String cmd1 = ctx.commandQueue().poll(5, TimeUnit.SECONDS);
        assertThat(cmd1).contains("\"type\":\"EXECUTE\"");
        engine.ackReceived(extractLine(cmd1));

        // G1 Z-1.5 (line 2)
        String cmd2 = ctx.commandQueue().poll(5, TimeUnit.SECONDS);
        assertThat(cmd2).contains("\"type\":\"EXECUTE\"");
        engine.ackReceived(extractLine(cmd2));

        // M30 triggers finish() directly — no EXECUTE sent
        assertThat(ctx.commandQueue().poll(2, TimeUnit.SECONDS)).isNull();

        waitForState(engine, MachineState.FINISHED);
        assertThat(ctx.stateChanges()).containsSubsequence(MachineState.RUNNING, MachineState.FINISHED);
    }

    @Test
    void pauseAndResume() throws Exception {
        TestContext ctx = createEngine();
        GCodeEngine engine = ctx.engine();

        engine.reset();
        engine.load("G21\nG0 X10\nG1 Z-1.0 F100\nM30");
        engine.start();

        // ACK G21, engine moves to G0
        String cmd1 = ctx.commandQueue().poll(5, TimeUnit.SECONDS);
        engine.ackReceived(extractLine(cmd1));

        // Wait for G0 to be sent (Thread A now blocked on ACK)
        String cmd2 = ctx.commandQueue().poll(5, TimeUnit.SECONDS);
        assertThat(cmd2).isNotNull();

        // Pause while Thread A is waiting for ACK of cmd2
        engine.pause();
        assertThat(engine.getState()).isEqualTo(MachineState.PAUSED);

        // ACK cmd2 — Thread A wakes up, sees PAUSED, returns without processing next
        engine.ackReceived(extractLine(cmd2));

        // Let Thread A fully exit before resuming to avoid a race on currentIndex
        Thread.sleep(100);

        // Resume — new thread picks up from G1 Z-1.0
        engine.resume();
        assertThat(engine.getState()).isEqualTo(MachineState.RUNNING);

        // ACK G1 Z-1.0
        String cmd3 = ctx.commandQueue().poll(5, TimeUnit.SECONDS);
        assertThat(cmd3).isNotNull();
        engine.ackReceived(extractLine(cmd3));

        // M30 -> finish
        waitForState(engine, MachineState.FINISHED);
        assertThat(ctx.stateChanges()).containsSubsequence(
                MachineState.RUNNING, MachineState.PAUSED, MachineState.RUNNING, MachineState.FINISHED);
    }

    @Test
    void stopDuringExecution() throws Exception {
        TestContext ctx = createEngine();
        GCodeEngine engine = ctx.engine();

        engine.reset();
        engine.load("G21\nG1 Z-1.0 F100\nM30");
        engine.start();

        // Wait for first EXECUTE (Thread A now blocked waiting for ACK)
        String cmd = ctx.commandQueue().poll(5, TimeUnit.SECONDS);
        assertThat(cmd).isNotNull();

        // Stop without sending ACK — cancels the pending future
        engine.stop();

        assertThat(engine.getState()).isEqualTo(MachineState.ERROR);
        assertThat(ctx.stateChanges()).contains(MachineState.ERROR);
    }

    @Test
    void invalidTransitionThrowsException() {
        TestContext ctx = createEngine();
        GCodeEngine engine = ctx.engine();
        // Engine starts in DISCONNECTED; start() tries DISCONNECTED -> RUNNING (invalid)

        assertThatThrownBy(engine::start)
                .isInstanceOf(MachineStateException.class)
                .hasMessageContaining("DISCONNECTED")
                .hasMessageContaining("RUNNING");
    }
}
