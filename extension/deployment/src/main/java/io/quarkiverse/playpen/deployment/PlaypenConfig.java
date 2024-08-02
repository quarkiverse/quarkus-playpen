package io.quarkiverse.playpen.deployment;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(name = "playpen", phase = ConfigPhase.BUILD_TIME)
public class PlaypenConfig {
    /**
     * Connection string for quarkus playpen.
     *
     * Uri. Add query parameters to uri for additional config parameters
     *
     * i.e.
     * http://host:port?who=whoami[&optional-config=value]*
     *
     * "who" is who you are. This is required.
     *
     * By default, all requests will be pushed locally from the proxy.
     * If you want to have a specific directed session, then use these parameters to define
     * the session within the playpen config uri:
     *
     * header - http header or cookie name that identifies the session id
     * query - query parameter name that identifies session id
     * path - path parameter name that identifies session id use "{}" to specify where sessionid is in path i.e.
     * /foo/bar/{}/blah
     * session - session id value
     */
    @ConfigItem
    public Optional<String> uri;

    /**
     * Credentials for creating a connection.
     *
     * If basic auth, use "username:password" for this value
     */
    @ConfigItem
    public Optional<String> credentials;

    /**
     * If true, quarkus will not connect to playpen on boot. Connection would have
     * to be done manually from the recorder method.
     *
     * This is for internal testing purposes only.
     */
    @ConfigItem(defaultValue = "false")
    public boolean manualStart;

}
