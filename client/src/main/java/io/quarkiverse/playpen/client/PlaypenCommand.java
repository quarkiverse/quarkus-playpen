package io.quarkiverse.playpen.client;

import java.util.concurrent.Callable;

import jakarta.inject.Inject;

import picocli.CommandLine;

@CommandLine.Command(name = "playpen")
public class PlaypenCommand implements Callable<Integer> {

    @CommandLine.Option(names = { "-p",
            "--port" }, defaultValue = "8080", description = "port of local process", showDefaultValue = CommandLine.Help.Visibility.ALWAYS)
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
            System.out.println(config.error);
            System.out.println();
            new CommandLine(new PlaypenCommand()).usage(System.out);
            return CommandLine.ExitCode.USAGE;
        }
        config.credentials = credentials;
        if (!client.start(port, config)) {
            System.out.println("Failed to start");
        }

        return 0;
    }
}
