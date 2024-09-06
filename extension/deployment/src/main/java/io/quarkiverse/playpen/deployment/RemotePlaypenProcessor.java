package io.quarkiverse.playpen.deployment;

import java.nio.file.Path;
import java.util.Set;

import org.jboss.logging.Logger;

import io.quarkiverse.playpen.client.RemotePlaypenClient;
import io.quarkiverse.playpen.utils.InsecureSsl;
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
        if (config.remote().isPresent() && config.local().isPresent()) {
            throw new BuildException("Must pick either quarkus.playpen.local or .remote");
        }
        return null;
    }

    @BuildStep
    public ArtifactResultBuildItem command(LiveReloadConfig liveReload, PlaypenConfig config, JarBuildItem jar)
            throws Exception {
        if (config.command().isPresent()) {
            RemotePlaypenClient client = getRemotePlaypenClient(liveReload, config);
            if (client == null) {
                return null;
            }
            testSelfSigned(config, client);
            String command = config.command().get();
            if ("remote-create-manual".equalsIgnoreCase(command)) {
                log.info("Creating remote playpen container, this may take awhile...");
                createRemote(client, jar, true);
            } else if ("remote-create".equalsIgnoreCase(command)) {
                log.info("Creating remote playpen container, this may take awhile...");
                if (createRemote(client, jar, false)) {
                    remoteGet(client);
                } else {
                    log.error("Failed to create remote playpen container!");
                }
            } else if ("remote-delete".equalsIgnoreCase(command)) {
                log.info("Deleting remote playpen container, this may take awhile...");
                deleteRemote(client);
            } else if ("remote-exists".equalsIgnoreCase(command)) {
                remoteExists(client);
            } else if ("remote-get".equalsIgnoreCase(command)) {
                remoteGet(client);
            } else if ("remote-download".equalsIgnoreCase(command)) {
                downloadRemote(client, jar);
            } else {
                log.error("Unknown remote playpen command: " + command);
                System.exit(1);
            }
            System.exit(0);
        }
        return null;
    }

    private void remoteExists(RemotePlaypenClient client) throws Exception {
        if (client.remotePlaypenExists()) {
            log.info("Remote playpen exists");
        } else {
            log.info("Remote playpen does not exist");
        }
    }

    private void remoteGet(RemotePlaypenClient client) throws Exception {
        String host = client.get();
        if (host == null) {
            log.info("Remote playpen does not exist");
        } else {
            log.info("Remote playpen host: " + host);
        }
    }

    private void downloadRemote(RemotePlaypenClient client, JarBuildItem jar) throws Exception {
        client.download(jar.getPath().getParent().getParent().resolve("download.zip"));
    }

    private boolean createRemote(RemotePlaypenClient client, JarBuildItem jar, boolean manual)
            throws Exception {
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

    private void deleteRemote(RemotePlaypenClient client)
            throws Exception {
        if (client.delete()) {
            log.info("Deletion of remote playpen container succeeded!");
        } else {
            log.error("Failed to delete remote playpen container!");
        }
    }

    static boolean alreadyInvoked = false;

    @BuildStep(onlyIf = IsRemoteDevClient.class)
    public ArtifactResultBuildItem playpen(LiveReloadConfig liveReload, PlaypenConfig config, JarBuildItem jar,
            CuratedApplicationShutdownBuildItem closeBuildItem)
            throws Exception {
        if (!config.remote().isPresent() || config.command().isPresent()) {
            return null;
        }
        if (alreadyInvoked) {
            return null;
        }
        RemotePlaypenClient client = getRemotePlaypenClient(liveReload, config);
        if (client == null)
            return null;
        testSelfSigned(config, client);
        // check credentials
        if (!client.challenge()) {
            System.exit(1);
            return null;
        }

        boolean createRemote = !client.isConnectingToExistingHost();
        boolean cleanupRemote = false;
        if (createRemote) {
            if (client.remotePlaypenExists()) {
                log.info("Remote playpen container already exists, not creating for session.");
            } else {
                log.info("Creating remote playpen container.  This may take awhile...");
                if (createRemote(jar, false, client)) {
                    cleanupRemote = true;
                } else {
                    log.error("Failed to create remote playpen container.");
                    System.exit(1);
                    return null;
                }
            }
        }

        log.info("Connecting to playpen");
        boolean status = client.connect(cleanupRemote);
        if (!status) {
            log.error("Failed to connect to playpen");
            System.exit(1);
            return null;
        }
        log.info("Connected to playpen!");
        alreadyInvoked = true;
        boolean finalCleanup = cleanupRemote;
        //  Use a regular shutdown hook and make sure it runs after remove dev client is done
        //  otherwise developer will see stack traces
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            long wait = 10;
            log.info("Waiting for quarkus:remote-dev to shutdown...");
            for (int i = 0; i < 30 && isThreadAlive("Remote dev client thread"); i++) {
                try {
                    Thread.sleep(wait);
                    if (wait < 1000)
                        wait *= 10;
                } catch (InterruptedException e) {

                }
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
            }
            callDisconnect(client, finalCleanup);
        }));
        return null;
    }

    private static void testSelfSigned(PlaypenConfig config, RemotePlaypenClient client) {
        Boolean selfSigned = client.isSelfSigned();
        if (selfSigned == null) {
            log.error("Invalid playpen url");
            System.exit(1);
        }
        if (selfSigned) {
            if (config.trustCert()) {
                InsecureSsl.trustAllByDefault();
            } else {
                log.warn(
                        "Playpen https url is self-signed. If you trust this endpoint, please specify quarkus.playpen.trust-cert=true");
                System.exit(1);
            }
        }
    }

    private boolean isThreadAlive(String search) {
        Set<Thread> threads = Thread.getAllStackTraces().keySet();
        for (Thread thread : threads) {
            if (thread.getName().contains(search) && (thread.isAlive() || thread.isInterrupted())) {
                return true;
            }
        }
        return false;
    }

    private static void callDisconnect(RemotePlaypenClient client, boolean finalCleanup) {
        try {
            if (finalCleanup) {
                log.info("Cleaning up remote playpen container, this may take awhile...");
            } else {
                log.info("Disconnecting from playpen...");
            }
            if (!client.disconnect()) {
                log.error("Failed to disconnect from playpen");
                return;
            }
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
        String url = config.remote().orElse("");
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
        String creds = config.credentials().orElse(liveReload.password.orElse(null));

        RemotePlaypenClient client = new RemotePlaypenClient(url, creds, queryString);
        return client;
    }
}
