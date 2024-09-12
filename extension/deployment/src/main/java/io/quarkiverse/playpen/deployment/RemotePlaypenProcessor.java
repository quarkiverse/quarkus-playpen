package io.quarkiverse.playpen.deployment;

import java.io.Closeable;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkiverse.playpen.client.KubernetesRemotePlaypenClient;
import io.quarkiverse.playpen.client.PortForward;
import io.quarkiverse.playpen.client.RemotePlaypenClient;
import io.quarkiverse.playpen.client.RemotePlaypenConnectionConfig;
import io.quarkiverse.playpen.server.PlaypenProxyConstants;
import io.quarkiverse.playpen.utils.InsecureSsl;
import io.quarkiverse.playpen.utils.ProxyUtils;
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
        int count = 0;
        if (config.local().connect().isPresent())
            count++;
        if (config.remote().connect().isPresent())
            count++;
        if (config.remote().create().isPresent())
            count++;
        if (config.remote().delete().isPresent())
            count++;
        if (config.remote().get().isPresent())
            count++;
        if (config.remote().exists().isPresent())
            count++;
        if (count > 1) {
            throw new BuildException("Too many playpen commands");

        }
        return null;
    }

    @BuildStep
    public ArtifactResultBuildItem create(CuratedApplicationShutdownBuildItem closeBuildItem, LiveReloadConfig liveReload,
            PlaypenConfig config, JarBuildItem jar)
            throws Exception {
        return null;
    }

    @BuildStep
    public ArtifactResultBuildItem command(CuratedApplicationShutdownBuildItem closeBuildItem, LiveReloadConfig liveReload,
            PlaypenConfig config, JarBuildItem jar)
            throws Exception {
        if (config.remote().create().isPresent()) {
            RemotePlaypenClient client = getRemotePlaypenClient(config.remote().create().get(),
                    closeBuildItem, liveReload, config);
            log.info("Creating remote playpen container, this may take awhile...");
            if (createRemote(client, jar, false)) {
                remoteGet(client);
            } else {
                log.error("Failed to create remote playpen container!");
            }
            exit();
        } else if (config.remote().delete().isPresent()) {
            RemotePlaypenClient client = getRemotePlaypenClient(config.remote().delete().get(),
                    closeBuildItem, liveReload, config);
            deleteRemote(client);
            exit();
        } else if (config.remote().get().isPresent()) {
            RemotePlaypenClient client = getRemotePlaypenClient(config.remote().get().get(),
                    closeBuildItem, liveReload, config);
            remoteGet(client);
            exit();
        } else if (config.remote().exists().isPresent()) {
            RemotePlaypenClient client = getRemotePlaypenClient(config.remote().exists().get(),
                    closeBuildItem, liveReload, config);
            remoteExists(client);
            exit();
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

    static List<Closeable> closeables = new ArrayList<>();

    @BuildStep(onlyIf = IsRemoteDevClient.class)
    public ArtifactResultBuildItem connect(LiveReloadConfig liveReload, PlaypenConfig config, JarBuildItem jar,
            CuratedApplicationShutdownBuildItem closeBuildItem)
            throws Exception {
        if (!config.remote().connect().isPresent()) {
            return null;
        }
        if (alreadyInvoked) {
            return null;
        }
        RemotePlaypenClient client = getRemotePlaypenClient(config.remote().connect().get(),
                closeBuildItem, liveReload, config);
        if (client == null)
            return null;
        boolean cleanupRemote = false;
        PortForward remoteForward = null;
        try {
            // check credentials
            if (!client.challenge()) {
                terminate();
                return null;
            }

            boolean createRemote = !client.isConnectingToExistingHost();
            cleanupRemote = false;
            if (createRemote) {
                if (client.remotePlaypenExists()) {
                    log.info("Remote playpen container already exists, not creating for session.");
                } else {
                    log.info("Creating remote playpen container.  This may take awhile...");
                    if (createRemote(jar, false, client)) {
                        cleanupRemote = true;
                    } else {
                        log.error("Failed to create remote playpen container.");
                        terminate();
                        return null;
                    }
                }
            }

            log.info("Connecting to playpen");
            boolean status = client.connect(cleanupRemote);
            if (!status) {
                log.error("Failed to connect to playpen");
                terminate();
                return null;
            }
            client.keepalive(5);

            alreadyInvoked = true;
            if (client instanceof KubernetesRemotePlaypenClient) {
                remoteForward = portForwardRemoteDevPod(liveReload, (KubernetesRemotePlaypenClient) client);
                closeables.add(remoteForward);
            }
        } catch (Throwable e) {
            log.error("Failed", e);
            terminate();
        }
        boolean finalCleanup = cleanupRemote;
        //  Use a regular shutdown hook and make sure it runs after remove dev client is done
        //  otherwise developer will see stack traces
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                client.shutdownKeepalive();
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
            } finally {
                endCloseables();
            }
        }));
        return null;
    }

    private static void endCloseables() {
        closeables.forEach(closeable -> ProxyUtils.safeClose(closeable));

    }

    private static void terminate() {
        endCloseables();
        System.exit(1);
    }

    private static void exit() {
        endCloseables();
        System.exit(0);
    }

    private static void testSelfSigned(RemotePlaypenClient client) {
        Boolean selfSigned = client.isSelfSigned();
        if (selfSigned == null) {
            log.error("Invalid playpen url");
            terminate();
        }
        if (selfSigned) {
            if (client.getConfig().trustCert) {
                InsecureSsl.trustAllByDefault();
            } else {
                log.warn(
                        "Playpen https url is self-signed. If you trust this endpoint, please specify quarkus.playpen.trust-cert=true");
                terminate();
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

    private static RemotePlaypenClient getRemotePlaypenClient(String command, CuratedApplicationShutdownBuildItem shutdown,
            LiveReloadConfig liveReload, PlaypenConfig config)
            throws Exception {
        RemotePlaypenConnectionConfig remoteConfig = new RemotePlaypenConnectionConfig();
        if (config.endpoint().isPresent()) {
            String cli = config.endpoint().get();
            if (!isPropertyBlank(cli)) {
                RemotePlaypenConnectionConfig.fromCli(remoteConfig, cli);
            }
        }

        // don't reload if -Dplaypen.local.xxxx and no value
        if (!isPropertyBlank(command)) {
            RemotePlaypenConnectionConfig.fromCli(remoteConfig, command);
        }
        if (remoteConfig.who == null) {
            log.error("playpen.remote.command -who must be set");
            terminate();
        }
        RemotePlaypenClient remoteClient = null;
        if (remoteConfig.connection == null && !liveReload.url.isPresent()) {
            log.warn(
                    "Cannot create remote playpen client.  playpen.remote must define a connection string, or, you must specify it within quarkus.live-reload.url");
            terminate();
            return null;
        } else if (remoteConfig.connection == null && liveReload.url.isPresent()) {
            String url = liveReload.url.get();
            int idx = url.indexOf(PlaypenProxyConstants.REMOTE_API_PATH);
            if (idx > 0) {
                url = url.substring(idx);
            }
            remoteConfig.connection = url;
            remoteClient = new RemotePlaypenClient(remoteConfig);
        } else if (remoteConfig.connection != null && !remoteConfig.connection.startsWith("http")) {
            // kubernetes connection
            KubernetesClient client = KubernetesClientUtils.createClient(config.kubernetesClient());
            KubernetesRemotePlaypenClient playpenClient = new KubernetesRemotePlaypenClient(client, remoteConfig);
            try {
                playpenClient.init();
                log.infov("Established port forward {0}", playpenClient.getPlaypenForward().toString());
            } catch (IllegalArgumentException e) {
                log.error("Failed to set up port forward to playpen: " + remoteConfig.connection);
                log.error(e.getMessage());
                terminate();
            }
            remoteClient = playpenClient;
        } else {
            remoteClient = new RemotePlaypenClient(remoteConfig);
        }
        testSelfSigned(remoteClient);
        closeables.add(remoteClient);
        return remoteClient;
    }

    public static boolean isPropertyBlank(String command) {
        return command == null || command.equals("true") || command.trim().isEmpty();
    }

    private static PortForward portForwardRemoteDevPod(LiveReloadConfig liveReload,
            KubernetesRemotePlaypenClient playpenClient) {
        if (liveReload.url.isPresent()) {
            URL liveUrl = null;
            try {
                liveUrl = new URL(liveReload.url.get());
                String host = liveUrl.getHost();
                if (!"localhost".equalsIgnoreCase(host) && !"127.0.0.1".equals(host)) {
                    log.warn(
                            "If you are using kubernetes port forwarding, quarkus.live-reload.url must be localhost");
                    terminate();
                }
                int port = liveUrl.getPort();
                if (port == -1) {
                    log.warn(
                            "If you are using kubernetes port forwarding, quarkus.live-reload.url must define an unset port to forward to");
                    terminate();
                }
                host = playpenClient.getConfig().host;
                if (host == null) {
                    host = "pod/" + playpenClient.getConfig().connection;
                    host += "-playpen-" + playpenClient.getConfig().who;
                }
                log.info("Setting up port forward for liveReload url: " + host);
                PortForward portForward = new PortForward(host);
                try {
                    portForward.forward(playpenClient.getClient(), port);
                    log.infov("Established port forward {0}", portForward.toString());
                } catch (IllegalArgumentException e) {
                    log.error(e.getMessage());
                    terminate();
                }
                return portForward;
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }
}
