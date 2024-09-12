package io.quarkiverse.playpen.deployment;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
@ConfigMapping(prefix = "playpen")
public interface PlaypenConfig {
    interface Local {

        /**
         *
         * @return
         */
        Optional<String> connect();

        /**
         *
         * @return
         */
        Optional<List<String>> portForwards();

        /**
         * If true, quarkus will not connect to local playpen on boot. Connection would have
         * to be done manually from the recorder method.
         *
         * This is for internal testing purposes only.
         */
        @WithDefault("false")
        boolean manualStart();
    }

    /**
     *
     * @return
     */
    Local local();

    interface Remote {

        /**
         *
         * @return
         */
        Optional<String> connect();

        /**
         *
         * @return
         */
        Optional<String> create();

        /**
         *
         * @return
         */
        Optional<String> delete();

        /**
         *
         * @return
         */
        Optional<String> exists();

        /**
         *
         * @return
         */
        Optional<String> get();
    }

    /**
     *
     * @return
     */
    Remote remote();

    /**
     * Provides defaults. application.properties is a great place for this.
     *
     *
     *
     * @return
     */
    Optional<String> endpoint();

    /**
     * Kubernetes client connection for establishing
     * port forwards to playpen
     *
     * @return
     */
    @ConfigDocSection(generated = true)
    PlaypenKubernetesClientBuildConfig kubernetesClient();

}
