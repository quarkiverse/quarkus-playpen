package io.quarkiverse.playpen.test.util.command;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:mstrukel@redhat.com">Marko Strukelj</a>
 */
public class CommandExec {

    public static final String WORK_DIR = System.getProperty("user.dir");

    public static final OsArch OS_ARCH = OsUtils.determineOSAndArch();

    private long waitTimeout = 30000;

    private Process process;

    private int exitCode = -1;

    private boolean logStreams = true;

    protected boolean dumpStreams = true;

    protected String workDir = WORK_DIR;

    private ByteArrayOutputStream stdout = new ByteArrayOutputStream();

    private ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    private InputStream stdin = new InteractiveInputStream();

    private Throwable err;

    private Thread stdoutRunner;

    private Thread stderrRunner;
    private Integer status;

    public Process process() {
        return process;
    }

    public CommandExec stdin(InputStream stdin) {
        this.stdin = stdin;
        return this;
    }

    public CommandExec workDir(String workDir) {
        this.workDir = workDir;
        return this;
    }

    public CommandExec dumpStreams(boolean dumpStreams) {
        this.dumpStreams = dumpStreams;
        return this;
    }

    public CommandExec logStreams(boolean logStreams) {
        this.logStreams = logStreams;
        return this;
    }

    public CommandExec waitTimeout(long waitTimeout) {
        this.waitTimeout = waitTimeout;
        return this;
    }

    public CommandExec execute(String cmd) {
        executeAsync(cmd);
        if (err == null) {
            waitCompletion();
        }
        return this;
    }

    public CommandExec executeAsync(String cmd) {

        try {
            System.out.println("Executing: " + cmd);

            process = Runtime.getRuntime().exec(cmd, null, new File(workDir));

            executeRunners();
        } catch (Throwable t) {
            err = t;
        }
        return this;
    }

    public CommandExec executeAsync(String... cmd) {

        try {
            System.out.print("Executing: ");
            for (String s : cmd)
                System.out.print(s);
            System.out.println();

            process = Runtime.getRuntime().exec(cmd, null, new File(workDir));
            executeRunners();
        } catch (Throwable t) {
            err = t;
        }
        return this;
    }

    private void executeRunners() {
        stdoutRunner = new StreamReaderThread(process.getInputStream(),
                logStreams ? new LoggingOutputStream("STDOUT", stdout) : stdout);
        stdoutRunner.start();

        stderrRunner = new StreamReaderThread(process.getErrorStream(),
                logStreams ? new LoggingOutputStream("STDERR", stderr) : stderr);
        stderrRunner.start();

        new StreamReaderThread(stdin, process.getOutputStream())
                .start();
    }

    public void exit() {
        process.destroy();
        try {
            process.waitFor(1000, TimeUnit.MILLISECONDS);
            Thread.sleep(100);
        } catch (InterruptedException e) {
        }
    }

    public void waitCompletion() {

        // This is necessary to make sure the process isn't stuck reading from stdin
        if (stdin instanceof InteractiveInputStream) {
            ((InteractiveInputStream) stdin).close();
        }
        try {
            if (process.waitFor(waitTimeout, TimeUnit.MILLISECONDS)) {
                exitCode = process.exitValue();
                if (exitCode != 0) {
                    dumpStreams = true;
                }
                // make sure reading output is really done (just in case)
                stdoutRunner.join(5000);
                stderrRunner.join(5000);
            } else {
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
                throw new RuntimeException("Timeout after " + (waitTimeout / 1000) + " seconds.");
            }
        } catch (InterruptedException e) {
            dumpStreams = true;
            throw new RuntimeException("Interrupted ...", e);
        } catch (Throwable t) {
            dumpStreams = true;
            err = t;
        } finally {
            if (!logStreams && dumpStreams)
                try {
                    System.out.println("STDOUT: ");
                    copyStream(new ByteArrayInputStream(stdout.toByteArray()), System.out);
                    System.out.println("STDERR: ");
                    copyStream(new ByteArrayInputStream(stderr.toByteArray()), System.out);
                } catch (Exception ignored) {
                }
        }
    }

    public int exitCode() {
        return exitCode;
    }

    public Throwable error() {
        return err;
    }

    public InputStream stdout() {
        return new ByteArrayInputStream(stdout.toByteArray());
    }

    public List<String> stdoutLines() {
        return parseStreamAsLines(new ByteArrayInputStream(stdout.toByteArray()));
    }

    public String stdoutString() {
        return new String(stdout.toByteArray(), StandardCharsets.UTF_8);
    }

    public InputStream stderr() {
        return new ByteArrayInputStream(stderr.toByteArray());
    }

    public List<String> stderrLines() {
        return filterAgentsOutput(parseStreamAsLines(new ByteArrayInputStream(stderr.toByteArray())));
    }

    public static List<String> filterAgentsOutput(List<String> lines) {
        return lines.stream().filter(line -> !line.contains("JAVA_TOOL_OPTIONS")).collect(Collectors.toList());
    }

    public String stderrString() {
        return new String(stderr.toByteArray());
    }

    static List<String> parseStreamAsLines(InputStream stream) {
        List<String> lines = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            return lines;
        } catch (IOException e) {
            throw new RuntimeException("Unexpected I/O error", e);
        }
    }

    public String waitForStdout(String... content) {
        long start = System.currentTimeMillis();
        String last = null;
        while (System.currentTimeMillis() - start < waitTimeout) {
            if (!process.isAlive())
                return null;
            last = stdoutString();
            for (String search : content) {
                if (last.indexOf(search) != -1) {
                    return search;
                }

            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted ...", e);
            }
        }

        throw new RuntimeException("Timed while waiting for content '" + content + "' to appear in stdout");
    }

    public void waitForStderr(String content) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < waitTimeout) {
            if (stderrString().indexOf(content) != -1) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted ...", e);
            }
        }

        throw new RuntimeException("Timed while waiting for content to appear in stderr");
    }

    public void sendToStdin(String s) {
        if (stdin instanceof InteractiveInputStream) {
            ((InteractiveInputStream) stdin).pushBytes(s.getBytes());
        } else {
            throw new RuntimeException("Can't push to stdin - not interactive");
        }
    }

    public void sendLine(String s) {
        sendToStdin(s + OsUtil.EOL);
    }

    static void copyStream(InputStream is, OutputStream os) throws IOException {
        byte[] buf = new byte[8192];

        try (InputStream iss = is) {
            int c;
            while ((c = iss.read(buf)) != -1) {
                os.write(buf, 0, c);
                os.flush();
            }
        }
    }
}
