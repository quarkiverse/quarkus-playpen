package io.quarkiverse.playpen.server;

public interface RemotePlaypenManager {
    boolean exists(String who);

    void create(String who);

    void delete(String who);
}
