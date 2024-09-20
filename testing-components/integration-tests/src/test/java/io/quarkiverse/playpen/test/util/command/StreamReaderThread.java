package io.quarkiverse.playpen.test.util.command;

import static io.quarkiverse.playpen.test.util.command.CommandExec.copyStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class StreamReaderThread extends Thread {

    private InputStream is;
    private OutputStream os;

    StreamReaderThread(InputStream is, OutputStream os) {
        this.is = is;
        this.os = os;
    }

    public void run() {
        try {
            copyStream(is, os);
        } catch (IOException e) {
        } finally {
            try {
                os.close();
            } catch (IOException ignored) {
            }
        }
    }
}
