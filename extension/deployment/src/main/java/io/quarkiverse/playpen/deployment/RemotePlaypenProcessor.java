package io.quarkiverse.playpen.deployment;

import java.nio.file.Path;
import java.util.Set;

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
        if (config.command.isPresent()) {
            String command = config.command.get();
            if ("remote-create-manual".equalsIgnoreCase(command)) {
                createRemote(liveReload, config, jar, true);
            } else if ("remote-create".equalsIgnoreCase(command)) {
                if (createRemote(liveReload, config, jar, false)) {
                    remoteGet(liveReload, config);
                }
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
            System.exit(0);
        }
        return null;
    }

    private void remoteExists(LiveReloadConfig liveReload, PlaypenConfig config) throws Exception {
        RemotePlaypenClient client = getRemotePlaypenClient(liveReload, config);
        if (client == null)
            return;
        if (client.remotePlaypenExists()) {
            log.info("Remote playpen exists");
        } else {
            log.info("Remote playpen does not exist");
        }
    }

    private void remoteGet(LiveReloadConfig liveReload, PlaypenConfig config) throws Exception {
        RemotePlaypenClient client = getRemotePlaypenClient(liveReload, config);
        if (client == null)
            return;
        String host = client.get();
        if (host == null) {
            log.info("Remote playpen does not exist");
        } else {
            log.info("Remote playpen host: " + host);
        }
    }

    private void downloadRemote(LiveReloadConfig liveReload, PlaypenConfig config, JarBuildItem jar) throws Exception {
        RemotePlaypenClient client = getRemotePlaypenClient(liveReload, config);
        if (client == null)
            return;
        client.download(jar.getPath().getParent().getParent().resolve("download.zip"));

    }

    private boolean createRemote(LiveReloadConfig liveReload, PlaypenConfig config, JarBuildItem jar, boolean manual)
            throws Exception {
        RemotePlaypenClient client = getRemotePlaypenClient(liveReload, config);
        if (client == null)
            return false;
        if (client.remotePlaypenExists()) {
            log.info("Remote playpen already exists, delete it first if you want to create a new one");
            return false;
        }
        return createRemote(jar, manual, client);
    }

    private boolean createRemote(JarBuildItem jar, boolean manual, RemotePlaypenClient client) throws Exception {
        Path zip = zip(jar);
        return client.create(zip, manual);
    }

    private void deleteRemote(LiveReloadConfig liveReload, PlaypenConfig config)
            throws Exception {

        RemotePlaypenClient client = getRemotePlaypenClient(liveReload, config);
        if (client == null)
            return;
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
        if (client == null)
            return null;
        // check credentials
        client.challenge();

        boolean createRemote = !client.isConnectingToExistingHost();
        boolean cleanupRemote = false;
        if (createRemote) {
            if (client.remotePlaypenExists()) {
                log.info("Remote playpen already exists, not creating for session.");
            } else {
                if (createRemote(jar, false, client)) {
                    cleanupRemote = true;
                } else {
                    return null;
                }
            }
        }

        boolean status = client.connect(cleanupRemote);
        if (!status) {
            log.error("Failed to connect to playpen");
            return null;
        }
        alreadyInvoked = true;
        boolean finalCleanup = cleanupRemote;
        //  Use a regular shutdown hook and make sure it runs after remove dev client is done
        //  otherwise developer will see stack traces
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Cleaning up remote playpen, this may take awhile...");
            long wait = 10;
            for (int i = 0; i < 30 && isThreadAlive("Remote dev client thread"); i++) {
                try {
                    if (wait < 1000)
                        wait *= 10;
                    Thread.sleep(wait);
                } catch (InterruptedException e) {

                }
            }
            callDisconnect(client, finalCleanup);
        }));
        /*
         * closeBuildItem.addCloseTask(() -> {
         * callDisconnect(client, finalCleanup);
         * }, true);
         *
         */
        return null;
    }

    private boolean isThreadAlive(String search) {
        Set<Thread> threads = Thread.getAllStackTraces().keySet();
        for (Thread thread : threads) {
            if (thread.getName().contains(search) && thread.isAlive()) {
                return true;
            }
        }
        return false;
    }

    private static void callDisconnect(RemotePlaypenClient client, boolean finalCleanup) {
        try {
            client.disconnect();
            if (finalCleanup) {
                boolean first = true;
                for (int i = 0; i < 30 && client.remotePlaypenExists(); i++) {
                    if (first) {
                        first = false;
                        log.info("Waiting for remote playpen cleanup...");
                    }
                    Thread.sleep(2000);
                }
            }
        } catch (Exception e) {
            log.error(e);
        }
    }

    private static RemotePlaypenClient getRemotePlaypenClient(LiveReloadConfig liveReload, PlaypenConfig config)
            throws Exception {
        String url = config.remote.orElse("");
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
                        "Cannot create remote playpen client.  quarkus.playpen.remote is not a full uri and quarkus.live-reload.url is not set");
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
