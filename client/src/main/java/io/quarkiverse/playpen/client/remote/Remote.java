package io.quarkiverse.playpen.client.remote;

import java.util.concurrent.Callable;

import io.quarkiverse.playpen.client.util.BaseCommand;
import picocli.CommandLine;

@CommandLine.Command(name = "remote", subcommands = Connect.class)
public class Remote extends BaseCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        output.info("Subcommands to work with remote playpens.");
        spec.commandLine().usage(output.out());

        output.info("");
        output.info("Use \"playpen remote <command> --help\" for more information about a given command.");
        return CommandLine.ExitCode.USAGE;
    }
}
