package io.quarkiverse.playpen.client.remote;

import static picocli.CommandLine.Help.Visibility.NEVER;

import java.util.concurrent.Callable;

import jakarta.inject.Inject;

import io.quarkiverse.playpen.client.OnShutdown;
import io.quarkiverse.playpen.client.RemotePlaypenClient;
import io.quarkiverse.playpen.client.util.BaseCommand;
import io.quarkiverse.playpen.client.util.MessageIcons;
import picocli.CommandLine;

@CommandLine.Command(name = "connect")
public class Connect extends BaseCommand implements Callable<Integer> {

    @CommandLine.Option(names = { "-c",
            "--credentials" }, defaultValue = "", description = "user:password or secret", showDefaultValue = NEVER)
    protected String credentials;

    @CommandLine.Option(names = {
            "--host" }, required = true, description = "host[:port] of remote playpen", showDefaultValue = NEVER)
    protected String host;

    @CommandLine.Parameters(index = "0", description = "URI of playpen server")
    protected String uri;

    @Inject
    protected OnShutdown shutdown;

    @Override
    public Integer call() throws Exception {
        String url = uri;
        String configString = null;
        int idx = url.indexOf('?');
        if (idx > -1) {
            configString = url.substring(idx + 1) + "&";
            url = url.substring(0, idx);
        }
        configString = configString + "host=" + host;

        RemotePlaypenClient client = new RemotePlaypenClient(url, credentials, configString);
        client.challenge();
        if (client.connect(false)) {
            output.info("Connected " + MessageIcons.SUCCESS_ICON);
            output.info("Hit @|bold <Control-C>|@ to exit and disconnect from playpen server");
            shutdown.await(() -> {
                try {
                    output.info("");
                    output.info("Disconnecting...");
                    client.disconnect();
                    output.info("Disconnect success " + MessageIcons.SUCCESS_ICON);
                } catch (Exception e) {
                    output.error(e.getMessage());
                }
            });
            return CommandLine.ExitCode.OK;
        } else {
            return CommandLine.ExitCode.SOFTWARE;
        }
    }
}
