package io.quarkiverse.playpen.client.util;

import static io.quarkiverse.playpen.utils.MessageIcons.ERROR_ICON;
import static io.quarkiverse.playpen.utils.MessageIcons.WARN_ICON;

import java.io.PrintWriter;
import java.text.MessageFormat;

import io.quarkiverse.playpen.client.PlaypenCommand;
import io.quarkiverse.playpen.utils.PlaypenLogger;
import picocli.CommandLine;
import picocli.CommandLine.Help.ColorScheme;
import picocli.CommandLine.Model.CommandSpec;

public class OutputMixin implements PlaypenLogger {
    static final boolean picocliDebugEnabled = "DEBUG".equalsIgnoreCase(System.getProperty("picocli.trace"));

    boolean verbose = false;

    @CommandLine.Option(names = { "-e", "--errors" }, description = "Print more context on errors and exceptions.")
    boolean showErrors;

    @CommandLine.Spec(CommandLine.Spec.Target.MIXEE)
    CommandSpec mixee;

    ColorScheme scheme;
    PrintWriter out;
    PrintWriter err;

    ColorScheme colorScheme() {
        ColorScheme colors = scheme;
        if (colors == null) {
            colors = scheme = mixee.commandLine().getColorScheme();
        }
        return colors;
    }

    public PrintWriter out() {
        PrintWriter o = out;
        if (o == null) {
            o = out = mixee.commandLine().getOut();
        }
        return o;
    }

    public PrintWriter err() {
        PrintWriter e = err;
        if (e == null) {
            e = err = mixee.commandLine().getErr();
        }
        return e;
    }

    public boolean isShowErrors() {
        return showErrors || picocliDebugEnabled;
    }

    private static OutputMixin getOutput(CommandSpec commandSpec) {
        return ((PlaypenCommand) commandSpec.root().userObject()).getOutput();
    }

    @CommandLine.Option(names = { "--verbose" }, description = "Verbose mode.")
    public void setVerbose(boolean verbose) {
        getOutput(mixee).verbose = verbose;
    }

    public boolean getVerbose() {
        return getOutput(mixee).verbose;
    }

    public boolean isVerbose() {
        return getVerbose() || picocliDebugEnabled;
    }

    public boolean isAnsiEnabled() {
        return CommandLine.Help.Ansi.AUTO.enabled();
    }

    private void printStackTrace(Throwable ex) {
        if (isShowErrors()) {
            err().println(colorScheme().stackTraceText(ex));
        }
    }

    @Override
    public String toString() {
        return "OutputOptions [showErrors=" + showErrors
                + ", verbose=" + getVerbose() + "]";
    }

    public void info(String msg) {
        out().println(colorScheme().ansi().new Text(msg, colorScheme()));
    }

    @Override
    public void infov(String msg, Object... params) {
        info(MessageFormat.format(msg, params));
    }

    @Override
    public void infof(String msg, Object... params) {
        info(String.format(msg, params));

    }

    public void error(String msg) {
        out().println(colorScheme().errorText(ERROR_ICON + " " + msg));
    }

    @Override
    public void error(String msg, Throwable cause) {
        error(msg);
        printStackTrace(cause);
    }

    @Override
    public void errorv(String msg, Object... params) {
        error(MessageFormat.format(msg, params));
    }

    @Override
    public void errorv(Throwable cause, String msg, Object... params) {
        error(MessageFormat.format(msg, params));
        printStackTrace(cause);
    }

    @Override
    public void errorf(String msg, Object... params) {
        error(String.format(msg, params));
    }

    @Override
    public void errorf(Throwable cause, String msg, Object... params) {
        error(String.format(msg, params));
        printStackTrace(cause);
    }

    public boolean isDebugEnabled() {
        return isVerbose();
    }

    public void debug(String msg) {
        if (isVerbose()) {
            out().println(colorScheme().ansi().new Text("@|faint [DEBUG] " + msg + "|@", colorScheme()));
        }
    }

    @Override
    public void debugv(String msg, Object... params) {
        if (isVerbose()) {
            debug(MessageFormat.format(msg, params));
        }
    }

    @Override
    public void debugf(String msg, Object... params) {
        if (isVerbose()) {
            debug(String.format(msg, params));
        }

    }

    @Override
    public void warn(String msg) {
        out().println(colorScheme().ansi().new Text("@|yellow " + WARN_ICON + " " + msg + "|@", colorScheme()));
    }

    @Override
    public void warnv(String msg, Object... params) {
        warnv(MessageFormat.format(msg, params));

    }

    @Override
    public void warnf(String msg, Object... params) {
        warnv(String.format(msg, params));
    }
}
