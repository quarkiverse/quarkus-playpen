package io.quarkiverse.playpen.client.remote;

import static picocli.CommandLine.Help.Visibility.NEVER;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import io.quarkiverse.playpen.client.RemotePlaypenClient;
import io.quarkiverse.playpen.client.util.BaseCommand;
import io.quarkus.runtime.Shutdown;
import picocli.CommandLine;

@CommandLine.Command(name = "connect")
public class Connect extends BaseCommand implements Callable<Integer> {

    @CommandLine.Option(names = { "-c",
            "--credentials" }, defaultValue = "", description = "user:password or secret", showDefaultValue = NEVER)
    private String credentials;

    @CommandLine.Option(names = { "-h",
            "--host" }, required = true, description = "host[:port] of remote playpen", showDefaultValue = NEVER)
    private String host;

    @CommandLine.Parameters(index = "0", description = "URI of playpen server")
    private String uri;

    RemotePlaypenClient client = null;
    CountDownLatch latch = null;

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

        client = new RemotePlaypenClient(url, credentials, configString);
        client.challenge();
        if (client.connect(false)) {
            latch = new CountDownLatch(1);
            latch.await();
            return CommandLine.ExitCode.OK;
        } else {
            return CommandLine.ExitCode.SOFTWARE;
        }
    }

    @Shutdown
    public void shutdown() throws Exception {
        if (client != null && latch != null) {
            client.disconnect();
        }

    }
}
