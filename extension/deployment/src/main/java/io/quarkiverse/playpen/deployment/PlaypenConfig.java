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

    /**
     * Config for local playpens
     */
    interface Local {

        /**
         * Connection string for local playpen
         *
         * @see <a href="https://github.com/quarkiverse/quarkus-playpen">Playpen Docs</a>
         * @return
         */
        Optional<String> connect();

        /**
         * Port forwards to localhost.
         * Comma delimited list of
         * [service|pod/][namespace/]name:[service port]:[local port]
         *
         * @see <a href="https://github.com/quarkiverse/quarkus-playpen">Playpen Docs</a>
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
     * Local playpen config
     *
     * @return
     */
    Local local();

    /**
     * Config for remote playpens
     */
    interface Remote {

        /**
         * Connection string for local playpen
         *
         * @see <a href="https://github.com/quarkiverse/quarkus-playpen">Playpen Docs</a>
         * @return
         */
        Optional<String> connect();

        /**
         * Create a temporary playpen pod from the source code in this project
         *
         * @return
         */
        Optional<String> create();

        /**
         * Delete temporary playpen pod
         *
         * @return
         */
        Optional<String> delete();

        /**
         * See if temporary playpen pod exists
         *
         * @return
         */
        Optional<String> exists();

        /**
         * Get name and port of temporary pod
         *
         * @return
         */
        Optional<String> get();
    }

    /**
     * Remote playpen config
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
