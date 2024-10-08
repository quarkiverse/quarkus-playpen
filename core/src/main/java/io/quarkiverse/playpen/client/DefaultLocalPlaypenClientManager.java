package io.quarkiverse.playpen.client;

import io.quarkiverse.playpen.utils.PlaypenLogger;

public class DefaultLocalPlaypenClientManager implements LocalPlaypenClientManager {
    protected final PlaypenLogger log = PlaypenLogger.getLogger(LocalPlaypenClientManager.class);

    @Override
    public LocalPlaypenConnectionConfig getConfig() {
        return config;
    }

    protected LocalPlaypenConnectionConfig config;

    public DefaultLocalPlaypenClientManager(LocalPlaypenConnectionConfig config) {
        this.config = config;
    }

    @Override
    public boolean checkHttpsCerts() {
        Boolean selfSigned = LocalPlaypenClient.isSelfSigned(config.connection);
        if (selfSigned == null) {
            log.error("Invalid playpen url: " + config.connection);
            return false;
        }
        if (selfSigned) {
            if (!config.trustCert) {
                log.warn(
                        "Playpen https url is self-signed. If you trust this endpoint, please specify --trustCert");
                return false;
            }
        }
        return true;
    }

}
