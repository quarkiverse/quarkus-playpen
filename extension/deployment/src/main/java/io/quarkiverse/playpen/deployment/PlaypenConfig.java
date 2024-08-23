package io.quarkiverse.playpen.deployment;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(name = "playpen", phase = ConfigPhase.BUILD_TIME)
public class PlaypenConfig {
    /**
     *
     */
    @ConfigItem
    public Optional<String> local;

    /**
     *
     */
    @ConfigItem
    public Optional<String> remote;

    /**
     * Credentials for creating a connection.
     *
     * If basic auth, use "username:password" for this value
     */
    @ConfigItem
    public Optional<String> credentials;

    /**
     * Execute a playpen command.
     * Only the command will execute. dev and remote-dev modes will not create a connection
     *
     * create-remote - creates a remote container based on this project.
     * delete-remote - delete a remote container
     */
    @ConfigItem
    public Optional<String> command;

    /**
     * If true, quarkus will not connect to local playpen on boot. Connection would have
     * to be done manually from the recorder method.
     *
     * This is for internal testing purposes only.
     */
    @ConfigItem(defaultValue = "false")
    public boolean manualStart;

}
