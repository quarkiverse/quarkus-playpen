package io.quarkiverse.playpen.client.local;

import java.util.concurrent.Callable;

import io.quarkiverse.playpen.client.util.BaseCommand;
import picocli.CommandLine;

@CommandLine.Command(name = "local", subcommands = Connect.class)
public class Local extends BaseCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        output.info("Subcommands to work with local playpens.");
        spec.commandLine().usage(output.out());

        output.info("");
        output.info("Use \"playpen local <command> --help\" for more information about a given command.");
        return CommandLine.ExitCode.USAGE;
    }
}
