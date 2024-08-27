package io.quarkiverse.playpen.utils;

public interface PlaypenLogger {
    public final class Factory {
        public static PlaypenLoggerFactory instance = new PlaypenLoggerFactory() {
            @Override
            public PlaypenLogger logger(Class name) {
                return new JbossLogger(name);
            }
        };

    }

    static PlaypenLogger getLogger(Class name) {
        return Factory.instance.logger(name);
    }

    boolean isDebugEnabled();

    void debug(String msg);

    void debugv(String msg, Object... params);

    void debugf(String msg, Object... params);

    void info(String msg);

    void infov(String msg, Object... params);

    void infof(String msg, Object... params);

    void warn(String msg);

    void warnv(String msg, Object... params);

    void warnf(String msg, Object... params);

    void error(String msg);

    void error(String msg, Throwable cause);

    void errorv(String msg, Object... params);

    void errorv(Throwable cause, String msg, Object... params);

    void errorf(String msg, Object... params);

    void errorf(Throwable cause, String msg, Object... params);
}
