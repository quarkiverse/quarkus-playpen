package io.quarkiverse.playpen.server;

public interface RemotePlaypenManager {
    /**
     * Returns hostname
     *
     * @param who
     * @return
     */
    String get(String who);

    String getHost(String host) throws IllegalArgumentException;

    boolean exists(String who);

    void create(String who, boolean copyEnv);

    void delete(String who);
}
