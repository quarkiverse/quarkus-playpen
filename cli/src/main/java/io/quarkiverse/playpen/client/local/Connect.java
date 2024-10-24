package io.quarkiverse.playpen.client.local;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import jakarta.inject.Inject;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkiverse.playpen.client.DefaultLocalPlaypenClientManager;
import io.quarkiverse.playpen.client.LocalPlaypenClient;
import io.quarkiverse.playpen.client.LocalPlaypenConnectionConfig;
import io.quarkiverse.playpen.client.OnShutdown;
import io.quarkiverse.playpen.client.util.BaseCommand;
import io.quarkiverse.playpen.client.util.ConnectMixin;
import io.quarkiverse.playpen.kubernetes.client.KubernetesLocalPlaypenClientManager;
import io.quarkiverse.playpen.kubernetes.client.PortForward;
import io.quarkiverse.playpen.utils.MessageIcons;
import io.quarkiverse.playpen.utils.ProxyUtils;
import io.vertx.core.Vertx;
import picocli.CommandLine;

@CommandLine.Command(name = "connect")
public class Connect extends BaseCommand implements Callable<Integer> {

    @CommandLine.Mixin
    protected ConnectMixin baseOptions;

    @CommandLine.Option(names = {
            "-onPoll", "--onPoll" }, defaultValue = "false", description = "route when client polling")
    public boolean onPoll;

    @CommandLine.Option(names = { "-l",
            "--local-port" }, defaultValue = "8080", description = "port of local process", showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
    private int localPort = 8080;

    @CommandLine.Option(names = { "-pf", "--pf", "-port-forward", "--port-forward" }, description = "establish port forwards")
    public List<String> portForwards;

    @Inject
    OnShutdown shutdown;

    @Inject
    Vertx vertx;

    @Inject
    KubernetesClient client;

    @Override
    public Integer call() throws Exception {

        LocalPlaypenConnectionConfig config = new LocalPlaypenConnectionConfig();
        if (!baseOptions.setConfig(output, config)) {
            return CommandLine.ExitCode.SOFTWARE;
        }
        LocalPlaypenConnectionConfig.setHttpLocation(config);
        config.onPoll = onPoll;
        if (config.error != null) {
            output.error(config.error);
            return CommandLine.ExitCode.SOFTWARE;
        }
        List<Closeable> closeables = new ArrayList<>();
        if (config.connection.startsWith("http")) {
            DefaultLocalPlaypenClientManager manager = new DefaultLocalPlaypenClientManager(config);
            if (!manager.checkHttpsCerts()) {
                System.exit(1);
            }
        } else {
            KubernetesLocalPlaypenClientManager manager = new KubernetesLocalPlaypenClientManager(config, client);
            try {
                PortForward portForward = manager.portForward();
                output.infov("Established playpen port forward {0}", portForward.toString());
                closeables.add(portForward);
            } catch (IllegalArgumentException e) {
                output.error(e.getMessage());
                output.error("Maybe playpen does not exist?");
                return CommandLine.ExitCode.SOFTWARE;
            }
            if (portForwards != null) {
                try {
                    portForwards.forEach(s -> {
                        PortForward pf = new PortForward(s);
                        pf.forward(client);
                        output.infov("Established port forward {0}", pf.toString());
                        closeables.add(pf);
                    });
                } catch (IllegalArgumentException e) {
                    output.error("Could not establish port forward");
                    output.error(e.getMessage());
                    closeables.forEach(ProxyUtils::safeClose);
                    return CommandLine.ExitCode.SOFTWARE;
                }
            }
        }
        LocalPlaypenClient client = LocalPlaypenClient.create(vertx)
                .playpen(config)
                .service("localhost", localPort, false)
                .credentials(config.credentials)
                .build();
        if (!client.start()) {
            output.error("Failed to start playpen client");
            closeables.forEach(ProxyUtils::safeClose);
            return CommandLine.ExitCode.SOFTWARE;
        }
        output.info("Connected " + MessageIcons.SUCCESS_ICON);
        output.info("Hit @|bold <Control-C>|@ to exit and disconnect from playpen server");
        shutdown.await(() -> {
            output.info("");
            output.info("Disconnecting...");
            try {
                client.shutdown();
            } finally {
                closeables.forEach(ProxyUtils::safeClose);
            }
            output.info("Disconnect success " + MessageIcons.SUCCESS_ICON);
        });
        return CommandLine.ExitCode.OK;
    }
}
