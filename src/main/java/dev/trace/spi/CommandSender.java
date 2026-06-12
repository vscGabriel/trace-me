package dev.trace.spi;

@FunctionalInterface
public interface CommandSender {
    void send(String jsonMessage);
}
