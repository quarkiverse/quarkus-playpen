package io.quarkiverse.playpen.client.local;

import java.util.concurrent.Callable;

import jakarta.inject.Inject;

import io.quarkiverse.playpen.client.OnShutdown;
import io.quarkiverse.playpen.client.PlaypenClient;
import io.quarkiverse.playpen.client.PlaypenConnectionConfig;
import io.quarkiverse.playpen.client.util.BaseCommand;
import io.quarkiverse.playpen.utils.MessageIcons;
import io.vertx.core.Vertx;
import picocli.CommandLine;

@CommandLine.Command(name = "connect")
public class Connect extends BaseCommand implements Callable<Integer> {

    @CommandLine.Option(names = { "-l",
            "--local-port" }, defaultValue = "8080", description = "port of local process", showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
    private int localPort = 8080;

    @CommandLine.Option(names = { "--trust-cert" }, defaultValue = "false", description = "port of local process", showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
    private boolean trustCert;

    @CommandLine.Option(names = { "-c",
            "--credentials" }, description = "user:password or secret")
    private String credentials;

    @CommandLine.Parameters(index = "0", description = "URI of playpen")
    private String uri;

    @Inject
    OnShutdown shutdown;

    @Inject
    Vertx vertx;

    @Override
    public Integer call() throws Exception {
        PlaypenConnectionConfig config = PlaypenConnectionConfig.fromUri(uri);
        if (config.error != null) {
            output.error(config.error);
            return CommandLine.ExitCode.SOFTWARE;
        }
        if (trustCert) {
            config.trustCert = true;
        } else {
            Boolean selfSigned = PlaypenClient.isSelfSigned(uri);
            if (selfSigned == null) {
                output.error("Invalid playpen url");
                System.exit(1);
            }
            if (selfSigned) {
                if (!trustCert) {
                    output.warn(
                            "Playpen https url is self-signed. If you trust this endpoint, please specify quarkus.playpen.trust-cert=true");
                    return CommandLine.ExitCode.SOFTWARE;
                }
            }
        }
        config.credentials = credentials;

        PlaypenClient client = PlaypenClient.create(vertx)
                .playpen(config)
                .service("localhost", localPort, false)
                .credentials(config.credentials)
                .build();
        if (!client.start()) {
            output.error("Failed to start playpen client");
            return CommandLine.ExitCode.SOFTWARE;
        }
        output.info("Connected " + MessageIcons.SUCCESS_ICON);
        output.info("Hit @|bold <Control-C>|@ to exit and disconnect from playpen server");
        shutdown.await(() -> {
            output.info("");
            output.info("Disconnecting...");
            client.shutdown();
            output.info("Disconnect success " + MessageIcons.SUCCESS_ICON);
        });
        return CommandLine.ExitCode.OK;
    }
}
