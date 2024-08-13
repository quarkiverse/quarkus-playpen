package io.quarkiverse.playpen.deployment;

import org.jboss.logging.Logger;

import io.quarkiverse.playpen.client.RemotePlaypenClient;
import io.quarkus.deployment.IsRemoteDevClient;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CuratedApplicationShutdownBuildItem;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.deployment.pkg.builditem.JarBuildItem;
import io.quarkus.runtime.LiveReloadConfig;

public class RemotePlaypenProcessor {
    private static final Logger log = Logger.getLogger(RemotePlaypenProcessor.class);

    @BuildStep(onlyIf = IsRemoteDevClient.class)
    public ArtifactResultBuildItem playpen(LiveReloadConfig liveReload, PlaypenConfig config, JarBuildItem jar,
            CuratedApplicationShutdownBuildItem closeBuildItem)
            throws Exception {
        if (!config.uri.isPresent()) {
            return null;
        }

        String url = config.uri.get();
        String queryString = "";
        if (url.contains("://")) {
            int idx = url.indexOf('?');
            if (idx > -1) {
                queryString = url.substring(idx + 1);
                url = url.substring(0, idx);
            }
        } else {
            if (!liveReload.url.isPresent()) {
                log.warn(
                        "Cannot start remote playpen.  quarkus.playpen.uri is not a full uri and quarkus.live-reload.url is not set");
                return null;
            }
            queryString = url;
            url = liveReload.url.get();
        }
        String creds = config.credentials.orElse(liveReload.password.orElse(null));

        boolean status = RemotePlaypenClient.connect(url, creds, queryString);
        if (!status) {
            log.error("Failed to connect to playpen");
        }
        String finalUrl = url;
        closeBuildItem.addCloseTask(() -> {
            try {
                RemotePlaypenClient.disconnect(finalUrl, creds);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, true);
        return null;
    }
}
