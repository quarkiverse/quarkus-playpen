package io.quarkiverse.playpen.test.util.command;

public class PlaypenCli extends CommandExec {
    protected String baseCmd = "java -jar ../../cli/target/playpen-cli-999-SNAPSHOT-runner.jar";

    @Override
    public PlaypenCli executeAsync(String cmd) {
        cmd = baseCmd + " " + cmd;
        return (PlaypenCli) super.executeAsync(cmd);
    }
}
