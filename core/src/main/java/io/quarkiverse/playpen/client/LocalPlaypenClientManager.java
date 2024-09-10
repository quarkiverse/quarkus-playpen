package io.quarkiverse.playpen.client;

public interface LocalPlaypenClientManager {
    LocalPlaypenConnectionConfig getConfig();

    boolean checkHttpsCerts();
}
