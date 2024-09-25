package io.quarkiverse.playpen.test.util.command;

import java.util.ArrayList;
import java.util.List;

public class PlaypenMvn extends CommandExec {
    String endpoint;

    public PlaypenMvn endpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    public class Remote {
        public PlaypenMvn create(String service) {
            return (PlaypenMvn) execute("mvn clean quarkus:remote-dev -Dplaypen.remote.create=" + service);
        }

        public PlaypenMvn delete(String service) {
            return (PlaypenMvn) execute("mvn clean quarkus:remote-dev -Dplaypen.remote.delete=" + service);
        }

        public PlaypenMvn exists(String service) {
            return (PlaypenMvn) execute("mvn clean quarkus:remote-dev -Dplaypen.remote.exists=" + service);
        }

        public PlaypenMvn get(String service) {
            return (PlaypenMvn) execute("mvn clean quarkus:remote-dev -Dplaypen.remote.get=" + service);
        }

        String liveCode;

        public Remote liveCode(String url) {
            liveCode = url;
            return this;
        }

        public Remote liveCode(int port) {
            liveCode = "http://localhost:" + port;
            return this;
        }

        public PlaypenMvn connect(String cli) {
            List<String> args = new ArrayList<>();
            args.add("mvn");
            args.add("quarkus:remote-dev");
            if (endpoint != null) {
                args.add("-Dplaypen.endpoint=" + endpoint);
            }
            if (liveCode != null) {
                args.add("-Dquarkus.live-reload.url=" + liveCode);
            }
            args.add("-Dplaypen.remote.connect=" + cli);
            return (PlaypenMvn) executeAsync(args.toArray(new String[args.size()]));
        }

    }

    public class Local {
        String endpoint;
        String portForwards;

        public Local portForward(String pf) {
            if (portForwards == null) {
                portForwards = pf;
            } else {
                portForwards = portForwards + "," + pf;
            }
            return this;
        }

        public PlaypenMvn connect(String cli) {
            List<String> args = new ArrayList<>();
            args.add("mvn");
            args.add("quarkus:dev");
            if (endpoint != null) {
                args.add("-Dplaypen.endpoint=" + endpoint);
            }
            if (portForwards != null) {
                args.add("-Dplaypen.local.port-forwards=" + portForwards);
            }
            args.add("-Dplaypen.local.connect=" + cli);
            return (PlaypenMvn) executeAsync(args.toArray(new String[args.size()]));
        }
    }

    public Remote remote() {
        return new Remote();
    }

    public Local local() {
        return new Local();
    }

    @Override
    public PlaypenMvn workDir(String workDir) {
        return (PlaypenMvn) super.workDir(workDir);
    }
}
