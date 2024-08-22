package io.quarkiverse.playpen.server;

public class MockRemotePlaypenManager implements RemotePlaypenManager {
    @Override
    public String get(String who) {
        return "";
    }

    @Override
    public boolean exists(String who) {
        return false;
    }

    @Override
    public void create(String who) {

    }

    @Override
    public void delete(String who) {

    }
}
