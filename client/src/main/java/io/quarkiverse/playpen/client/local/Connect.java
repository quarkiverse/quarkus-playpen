package io.quarkiverse.playpen.client.local;

import java.util.concurrent.Callable;

import jakarta.inject.Inject;

import io.quarkiverse.playpen.client.PlaypenConnectionConfig;
import io.quarkiverse.playpen.client.util.BaseCommand;
import picocli.CommandLine;

@CommandLine.Command(name = "connect")
public class Connect extends BaseCommand implements Callable<Integer> {

    @CommandLine.Option(names = { "-p",
            "--target-port" }, defaultValue = "8080", description = "port of local process", showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
    private int port = 8080;

    @CommandLine.Option(names = { "-c",
            "--credentials" }, description = "user:password or secret")
    private String credentials;

    @CommandLine.Parameters(index = "0", description = "URI of playpen")
    private String uri;

    @Inject
    PlaypenClientBean client;

    @Override
    public Integer call() throws Exception {
        PlaypenConnectionConfig config = PlaypenConnectionConfig.fromUri(uri);
        if (config.error != null) {
            output.error(config.error);
            return CommandLine.ExitCode.SOFTWARE;
        }
        config.credentials = credentials;
        if (!client.start(port, config)) {
            output.error("Failed to start");
            return CommandLine.ExitCode.SOFTWARE;
        }

        return CommandLine.ExitCode.OK;
    }
}
