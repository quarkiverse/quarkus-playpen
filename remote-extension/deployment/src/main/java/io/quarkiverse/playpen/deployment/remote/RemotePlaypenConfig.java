package io.quarkiverse.playpen.deployment.remote;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(name = "playpen", phase = ConfigPhase.BUILD_TIME)
public class RemotePlaypenConfig {
    /**
     * Config string for quarkus playpen. Values separated by "&" character
     *
     * header - http header or cookie name that identifies the session id
     * query - query parameter name that identifies session id
     * path - path parameter name that identifies session id use "{}" to specify where sessionid is in path i.e.
     * /foo/bar/{}/blah
     */
    @ConfigItem
    public Optional<String> config;
}
