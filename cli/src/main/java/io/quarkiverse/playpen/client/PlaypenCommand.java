package io.quarkiverse.playpen.client;

import java.util.concurrent.Callable;

import io.quarkiverse.playpen.client.local.Local;
import io.quarkiverse.playpen.client.remote.Remote;
import io.quarkiverse.playpen.client.util.BaseCommand;
import io.quarkiverse.playpen.client.util.OutputMixin;
import io.quarkiverse.playpen.utils.PlaypenLogger;
import io.quarkiverse.playpen.utils.PlaypenLoggerFactory;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine;

@TopCommand
@CommandLine.Command(name = "playpen", subcommands = { Local.class, Remote.class })
public class PlaypenCommand extends BaseCommand implements Callable<Integer> {
    public PlaypenCommand() {
        PlaypenLogger.Factory.instance = new PlaypenLoggerFactory() {
            @Override
            public PlaypenLogger logger(Class clz) {
                return getOutput();
            }
        };
    }

    public OutputMixin getOutput() {
        return output;
    }

    @Override
    public Integer call() throws Exception {
        output.info("@|bold Quarkus Playpen|@");

        spec.commandLine().usage(output.out());

        output.info("");
        output.info("Use \"playpen <command> --help\" for more information about a given command.");

        return spec.exitCodeOnUsageHelp();
    }
}
