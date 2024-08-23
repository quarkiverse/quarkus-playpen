package io.quarkiverse.playpen.deployment;

import java.nio.file.Path;

import org.jboss.logging.Logger;

import io.quarkiverse.playpen.client.RemotePlaypenClient;
import io.quarkus.builder.BuildException;
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
    public ArtifactResultBuildItem check(PlaypenConfig config) throws Exception {
        if (config.remote.isPresent() && config.local.isPresent()) {
            throw new BuildException("Must pick either quarkus.playpen.local or .remote");
        }
        return null;
    }

    @BuildStep
    public ArtifactResultBuildItem command(LiveReloadConfig liveReload, PlaypenConfig config, JarBuildItem jar)
            throws Exception {
        if (config.command.isPresent() && config.remote.isPresent()) {
            String command = config.command.get();
            if ("create-remote-manual".equalsIgnoreCase(command)) {
                createRemote(liveReload, config, jar, true);
            } else if ("remote-create".equalsIgnoreCase(command)) {
                createRemote(liveReload, config, jar, false);
            } else if ("remote-delete".equalsIgnoreCase(command)) {
                deleteRemote(liveReload, config);
            } else if ("remote-exists".equalsIgnoreCase(command)) {
                remoteExists(liveReload, config);
            } else if ("remote-get".equalsIgnoreCase(command)) {
                remoteGet(liveReload, config);
            } else if ("remote-download".equalsIgnoreCase(command)) {
                downloadRemote(liveReload, config, jar);
            } else {
                log.error("Unknown remote playpen command: " + command);
            }
        }
        System.exit(0);
        return null;
    }

    private void remoteExists(LiveReloadConfig liveReload, PlaypenConfig config) throws Exception {
        RemotePlaypenClient client = getRemotePlaypenClient(liveReload, config);
        if (client.remotePlaypenExists()) {
            log.info("Remote playpen exists");
        } else {
            log.info("Remote does not exists");
        }
    }

    private void remoteGet(LiveReloadConfig liveReload, PlaypenConfig config) throws Exception {
        RemotePlaypenClient client = getRemotePlaypenClient(liveReload, config);
        String host = client.get();
        if (host == null) {
            log.info("Remote does not exists");
        } else {
            log.info("Remote playpen host: " + host);
        }
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

    private void deleteRemote(LiveReloadConfig liveReload, PlaypenConfig config)
            throws Exception {

        RemotePlaypenClient client = getRemotePlaypenClient(liveReload, config);
        client.delete();
    }

    static boolean alreadyInvoked = false;

    @BuildStep(onlyIf = IsRemoteDevClient.class)
    public ArtifactResultBuildItem playpen(LiveReloadConfig liveReload, PlaypenConfig config, JarBuildItem jar,
            CuratedApplicationShutdownBuildItem closeBuildItem)
            throws Exception {
        if (!config.remote.isPresent() || config.command.isPresent()) {
            return null;
        }
        if (alreadyInvoked) {
            return null;
        }
        RemotePlaypenClient client = getRemotePlaypenClient(liveReload, config);
        // check credentials
        client.challenge();

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
        String url = config.remote.get();
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
