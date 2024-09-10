package io.quarkiverse.playpen.client.local;

import java.io.Closeable;
import java.util.concurrent.Callable;

import jakarta.inject.Inject;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.quarkiverse.playpen.client.DefaultLocalPlaypenClientManager;
import io.quarkiverse.playpen.client.KubernetesLocalPlaypenClientManager;
import io.quarkiverse.playpen.client.LocalPlaypenConnectionConfig;
import io.quarkiverse.playpen.client.OnShutdown;
import io.quarkiverse.playpen.client.PlaypenClient;
import io.quarkiverse.playpen.client.PortForward;
import io.quarkiverse.playpen.client.util.BaseCommand;
import io.quarkiverse.playpen.client.util.ConnectMixin;
import io.quarkiverse.playpen.utils.MessageIcons;
import io.quarkiverse.playpen.utils.ProxyUtils;
import io.vertx.core.Vertx;
import picocli.CommandLine;

@CommandLine.Command(name = "connect")
public class Connect extends BaseCommand implements Callable<Integer> {

    @CommandLine.Mixin
    protected ConnectMixin baseOptions;

    @CommandLine.Option(names = { "-l",
            "--local-port" }, defaultValue = "8080", description = "port of local process", showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
    private int localPort = 8080;

    @Inject
    OnShutdown shutdown;

    @Inject
    Vertx vertx;

    @Override
    public Integer call() throws Exception {

        LocalPlaypenConnectionConfig config = new LocalPlaypenConnectionConfig();
        baseOptions.setConfig(config);
        if (config.error != null) {
            output.error(config.error);
            return CommandLine.ExitCode.SOFTWARE;
        }
        PortForward portForward = null;
        if (config.connection.startsWith("http")) {
            DefaultLocalPlaypenClientManager manager = new DefaultLocalPlaypenClientManager(config);
            if (!manager.checkHttpsCerts()) {
                System.exit(1);
            }
        } else {
            KubernetesClient client = new KubernetesClientBuilder().build();
            KubernetesLocalPlaypenClientManager manager = new KubernetesLocalPlaypenClientManager(config, client);
            try {
                portForward = manager.portForward();
                output.infov("Established port forward {0}", portForward.toString());
            } catch (IllegalArgumentException e) {
                output.error("Failed to set up port forward to playpen: " + config.connection);
                output.error(e.getMessage());
                return CommandLine.ExitCode.SOFTWARE;
            }
        }
        PlaypenClient client = PlaypenClient.create(vertx)
                .playpen(config)
                .service("localhost", localPort, false)
                .credentials(config.credentials)
                .build();
        if (!client.start()) {
            output.error("Failed to start playpen client");
            ProxyUtils.safeClose(portForward);
            return CommandLine.ExitCode.SOFTWARE;
        }
        output.info("Connected " + MessageIcons.SUCCESS_ICON);
        output.info("Hit @|bold <Control-C>|@ to exit and disconnect from playpen server");
        Closeable finalPortForward = portForward;
        shutdown.await(() -> {
            output.info("");
            output.info("Disconnecting...");
            try {
                client.shutdown();
            } finally {
                ProxyUtils.safeClose(finalPortForward);
            }
            output.info("Disconnect success " + MessageIcons.SUCCESS_ICON);
        });
        return CommandLine.ExitCode.OK;
    }
}
