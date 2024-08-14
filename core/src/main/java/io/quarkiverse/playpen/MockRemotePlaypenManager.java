package io.quarkiverse.playpen;

import io.quarkiverse.playpen.server.RemotePlaypenManager;

public class MockRemotePlaypenManager implements RemotePlaypenManager {
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
