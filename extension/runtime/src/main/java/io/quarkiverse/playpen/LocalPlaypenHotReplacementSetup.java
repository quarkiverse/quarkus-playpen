package io.quarkiverse.playpen;

import org.jboss.logging.Logger;

import io.quarkus.dev.spi.HotReplacementContext;
import io.quarkus.dev.spi.HotReplacementSetup;

public class LocalPlaypenHotReplacementSetup implements HotReplacementSetup {
    private static final Logger log = Logger.getLogger(LocalPlaypenHotReplacementSetup.class);

    @Override
    public void setupHotDeployment(HotReplacementContext context) {
    }

    @Override
    public void close() {
        LocalPlaypenRecorder.closeSession();
    }

}
