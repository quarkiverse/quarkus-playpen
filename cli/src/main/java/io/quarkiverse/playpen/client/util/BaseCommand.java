package io.quarkiverse.playpen.client.util;

import picocli.CommandLine;

public class BaseCommand {
    @CommandLine.Mixin
    protected HelpOption helpOption;
    @CommandLine.Spec
    protected CommandLine.Model.CommandSpec spec;
    @CommandLine.Mixin(name = "output")
    protected OutputMixin output;
}
