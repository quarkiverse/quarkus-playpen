package io.quarkiverse.playpen.deployment;

import java.nio.file.Path;

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

    private Path zip(JarBuildItem jar) throws Exception {
        Path dst = jar.getPath().getParent().getParent().resolve("upload.zip");
        ZipDirectory.zip(jar.getPath().getParent(), dst);
        return dst;
    }

    @BuildStep
    public ArtifactResultBuildItem command(LiveReloadConfig liveReload, PlaypenConfig config, JarBuildItem jar)
            throws Exception {
        if (config.command.isPresent()) {
            String command = config.command.get();
            if ("create-remote-manual".equalsIgnoreCase(command)) {
                createRemote(liveReload, config, jar, true);
            } else if ("create-remote".equalsIgnoreCase(command)) {
                createRemote(liveReload, config, jar, false);
            } else if ("download-remote".equalsIgnoreCase(command)) {
                downloadRemote(liveReload, config, jar);
            } else {
                log.error("Illegal playpen command: " + command);
            }
        }
        return null;
    }

    private void downloadRemote(LiveReloadConfig liveReload, PlaypenConfig config, JarBuildItem jar) throws Exception {
        RemotePlaypenClient client = getRemotePlaypenClient(liveReload, config);
        client.download(jar.getPath().getParent().getParent().resolve("download.zip"));

    }

    private boolean createRemote(LiveReloadConfig liveReload, PlaypenConfig config, JarBuildItem jar, boolean manual)
            throws Exception {
        RemotePlaypenClient client = getRemotePlaypenClient(liveReload, config);
        Path zip = zip(jar);
        return client.create(zip, manual);
    }

    static boolean alreadyInvoked = false;

    @BuildStep(onlyIf = IsRemoteDevClient.class)
    public ArtifactResultBuildItem playpen(LiveReloadConfig liveReload, PlaypenConfig config, JarBuildItem jar,
            CuratedApplicationShutdownBuildItem closeBuildItem)
            throws Exception {
        if (!config.uri.isPresent() || config.command.isPresent()) {
            return null;
        }
        if (alreadyInvoked) {
            return null;
        }
        RemotePlaypenClient client = getRemotePlaypenClient(liveReload, config);

        boolean createRemote = !client.isConnectingToExistingHost();
        if (createRemote && !createRemote(liveReload, config, jar, false)) {
            return null;
        }

        boolean status = client.connect();
        if (!status) {
            log.error("Failed to connect to playpen");
            return null;
        }
        alreadyInvoked = true;
        closeBuildItem.addCloseTask(() -> {
            try {
                client.disconnect();
                if (createRemote) {
                    log.info("Waiting for remote playpen cleanup...");
                    for (int i = 0; i < 30; i++) {
                        if (client.remotePlaypenExists()) {
                            break;
                        }
                        Thread.sleep(2000);
                    }
                }
            } catch (Exception e) {
                log.error(e);
            }
        }, true);
        return null;
    }

    private static RemotePlaypenClient getRemotePlaypenClient(LiveReloadConfig liveReload, PlaypenConfig config)
            throws Exception {
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
                        "Cannot create remote playpen client.  quarkus.playpen.uri is not a full uri and quarkus.live-reload.url is not set");
                return null;
            }
            queryString = url;
            url = liveReload.url.get();
        }
        String creds = config.credentials.orElse(liveReload.password.orElse(null));

        RemotePlaypenClient client = new RemotePlaypenClient(url, creds, queryString);
        return client;
    }
}
