package io.quarkiverse.playpen.client.remote;

import static picocli.CommandLine.Help.Visibility.NEVER;

import java.util.concurrent.Callable;

import jakarta.inject.Inject;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.quarkiverse.playpen.client.KubernetesRemotePlaypenClient;
import io.quarkiverse.playpen.client.OnShutdown;
import io.quarkiverse.playpen.client.RemotePlaypenClient;
import io.quarkiverse.playpen.client.RemotePlaypenConnectionConfig;
import io.quarkiverse.playpen.client.util.BaseCommand;
import io.quarkiverse.playpen.client.util.ConnectMixin;
import io.quarkiverse.playpen.utils.InsecureSsl;
import io.quarkiverse.playpen.utils.MessageIcons;
import picocli.CommandLine;

@CommandLine.Command(name = "connect")
public class Connect extends BaseCommand implements Callable<Integer> {

    @CommandLine.Mixin
    protected ConnectMixin baseOptions;

    @CommandLine.Option(names = {
            "-host", "--host" }, required = true, description = "host[:port] of remote playpen", showDefaultValue = NEVER)
    protected String host;

    @Inject
    protected OnShutdown shutdown;

    @Override
    public Integer call() throws Exception {
        RemotePlaypenConnectionConfig config = new RemotePlaypenConnectionConfig();
        baseOptions.setConfig(config);
        config.host = host;

        RemotePlaypenClient client = null;
        if (config.connection.startsWith("http")) {
            client = new RemotePlaypenClient(config);
        } else {
            KubernetesClient kube = new KubernetesClientBuilder().build();
            KubernetesRemotePlaypenClient kc = new KubernetesRemotePlaypenClient(kube, config);
            client = kc;
            try {
                kc.init();
                output.infov("Established port forward {0}", kc.getPlaypenForward().toString());
            } catch (IllegalArgumentException e) {
                output.error("Failed to set up port forward to playpen: " + config.connection);
                output.error(e.getMessage());
                return CommandLine.ExitCode.SOFTWARE;
            }
        }
        Boolean selfSigned = client.isSelfSigned();
        if (selfSigned == null) {
            output.error("Invalid playpen url");
            return CommandLine.ExitCode.SOFTWARE;
        }
        if (selfSigned) {
            if (config.trustCert) {
                InsecureSsl.trustAllByDefault();
            } else {
                output.warn(
                        "Playpen https url is self-signed. If you trust this endpoint, please specify --trustCert=true");
                return CommandLine.ExitCode.SOFTWARE;
            }
        }
        client.challenge();
        if (client.connect(false)) {
            output.info("Connected " + MessageIcons.SUCCESS_ICON);
            output.info("Hit @|bold <Control-C>|@ to exit and disconnect from playpen server");
            RemotePlaypenClient finalClient = client;
            client.keepalive(5);
            shutdown.await(() -> {
                try {
                    finalClient.shutdownKeepalive();
                    output.info("");
                    output.info("Disconnecting...");
                    finalClient.disconnect();
                    output.info("Disconnect success " + MessageIcons.SUCCESS_ICON);
                } catch (Exception e) {
                    output.error(e.getMessage());
                } finally {
                    finalClient.close();
                }
            });
            return CommandLine.ExitCode.OK;
        } else {
            client.close();
            return CommandLine.ExitCode.SOFTWARE;
        }
    }
}
