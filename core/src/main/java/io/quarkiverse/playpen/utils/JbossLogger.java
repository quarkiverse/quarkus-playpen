package io.quarkiverse.playpen.utils;

import org.jboss.logging.Logger;

public class JbossLogger implements PlaypenLogger {
    private final Logger log;

    public JbossLogger(String name) {
        log = Logger.getLogger(name);
    }

    public JbossLogger(Class clz) {
        log = Logger.getLogger(clz);
    }

    @Override
    public boolean isDebugEnabled() {
        return log.isDebugEnabled();
    }

    @Override
    public void debug(String msg) {
        log.debug(msg);
    }

    @Override
    public void debugv(String msg, Object... params) {
        log.debugv(msg, params);
    }

    @Override
    public void debugf(String msg, Object... params) {
        log.debugf(msg, params);

    }

    @Override
    public void info(String msg) {
        log.info(msg);
    }

    @Override
    public void infov(String msg, Object... params) {
        log.infov(msg, params);

    }

    @Override
    public void infof(String msg, Object... params) {
        log.infov(msg, params);

    }

    @Override
    public void warn(String msg) {
        log.warn(msg);

    }

    @Override
    public void warnv(String msg, Object... params) {
        log.warnv(msg, params);
    }

    @Override
    public void warnf(String msg, Object... params) {
        log.warnf(msg, params);
    }

    @Override
    public void error(String msg) {
        log.error(msg);
    }

    @Override
    public void error(String msg, Throwable cause) {
        log.error(msg, cause);

    }

    @Override
    public void errorv(String msg, Object... params) {
        log.errorv(msg, params);
    }

    @Override
    public void errorv(Throwable cause, String msg, Object... params) {
        log.errorv(cause, msg, params);
    }

    @Override
    public void errorf(String msg, Object... params) {
        log.errorf(msg, params);

    }

    @Override
    public void errorf(Throwable cause, String msg, Object... params) {
        log.errorf(cause, msg, params);

    }
}
