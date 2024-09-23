package org.acme;

import java.net.URI;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

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
    public interface Greeting {
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        String hello(@QueryParam("user") String user);

    }

    @ConfigProperty(name = "greeting.host")
    @Inject
    String greetingUri;

    @ConfigProperty(name = "meeting.mode", defaultValue = "none")
    @Inject
    String meetingMode;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String meet(@QueryParam("user") @DefaultValue("developer") String user) throws Exception {
        Greeting greeting = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create("http://" + greetingUri)).build(Greeting.class);

        String msg = greeting.hello(user);
        if (!"none".equals(meetingMode)) {
            msg += " " + meetingMode;
        }
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        msg = "<h1>" + msg + "</h1><h1>Let's meet locally at " + time + "</h1>";
        return msg;
    }
}
