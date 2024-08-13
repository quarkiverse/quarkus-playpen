package io.quarkiverse.playpen.deployment;

import java.util.function.BooleanSupplier;

import io.quarkus.dev.spi.DevModeType;

public class IsAnyRemoteDev implements BooleanSupplier {

    private final DevModeType devModeType;

    public IsAnyRemoteDev(DevModeType devModeType) {
        this.devModeType = devModeType;
    }

    @Override
    public boolean getAsBoolean() {
        return devModeType == DevModeType.REMOTE_LOCAL_SIDE || devModeType == DevModeType.REMOTE_SERVER_SIDE;
    }
}
