package io.quarkiverse.playpen.server;

public interface RemotePlaypenManager {
    /**
     * Returns hostname
     *
     * @param who
     * @return
     */
    String get(String who);

    boolean exists(String who);

    void create(String who);

    void delete(String who);
}
