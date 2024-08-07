package io.quarkiverse.playpen.deployment.remote;

import org.jboss.logging.Logger;

import io.quarkiverse.playpen.client.RemotePlaypen;
import io.quarkus.deployment.IsRemoteDevClient;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CuratedApplicationShutdownBuildItem;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.deployment.pkg.builditem.JarBuildItem;
import io.quarkus.runtime.LiveReloadConfig;

public class RemotePlaypenProcessor {
    private static final Logger log = Logger.getLogger(RemotePlaypenProcessor.class);

    @BuildStep(onlyIf = IsRemoteDevClient.class)
    public ArtifactResultBuildItem playpen(LiveReloadConfig liveReload, RemotePlaypenConfig config, JarBuildItem jar,
            CuratedApplicationShutdownBuildItem closeBuildItem)
            throws Exception {
        if (!config.config.isPresent()) {
            return null;
        }
        if (!liveReload.url.isPresent()) {
            log.warn("quarkus.live-reload.url is not set");
            return null;
        }
        if (!liveReload.password.isPresent()) {
            log.warn("quarkus.live-reload.password is not set");
            return null;
        }
        log.info("************************");
        log.info("Remote Playpen Processor");
        log.info("************************");

        boolean status = RemotePlaypen.connect(liveReload.url.get(), liveReload.password.get(), config.config.get());
        if (!status) {
            log.error("Failed to connect to playpen");
        }
        log.info("--------------------->  DONE");
        closeBuildItem.addCloseTask(() -> {
            try {
                RemotePlaypen.disconnect(liveReload.url.get(), liveReload.password.get());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, true);
        return null;
    }
}
