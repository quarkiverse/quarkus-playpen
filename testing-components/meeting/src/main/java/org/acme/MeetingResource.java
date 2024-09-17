package org.acme;

import java.net.URI;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

@Path("/meet")
public class MeetingResource {
    @Path("/hello")
    //@RegisterRestClient(configKey = "greeting.service")
    public interface Greeting {
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        String hello(@QueryParam("user") String user);

    }

    @Path("/goodbye")
    //@RegisterRestClient(configKey = "farewell.service")
    public interface Farewell {
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        String goodbye(@QueryParam("user") String user);

    }

    @ConfigProperty(name = "greeting.service")
    @Inject
    String greetingUri;

    @ConfigProperty(name = "farewell.service")
    @Inject
    String farewellUri;

    //@RestClient
    //Farewell farewell;

    //@RestClient
    //Greeting greeting;

    @ConfigProperty(name = "meeting.mode", defaultValue = "none")
    @Inject
    String meetingMode;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String meet(@QueryParam("user") @DefaultValue("developer") String user) throws Exception {
        if (false) {
            return greetingUri + " " + farewellUri + " " + meetingMode;
        }
        Greeting greeting = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(greetingUri)).build(Greeting.class);
        Farewell farewell = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(farewellUri)).build(Farewell.class);

        String msg = greeting.hello(user);
        msg += " ";
        msg += farewell.goodbye(user);
        if (!"none".equals(meetingMode)) {
            msg += " " + meetingMode;
        }
        return msg;
    }
}
