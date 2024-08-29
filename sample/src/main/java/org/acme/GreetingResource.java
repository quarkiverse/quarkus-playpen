package org.acme;

import io.quarkiverse.playpen.Playpen;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

@Path("/hello")
public class GreetingResource {
    @GET
    @Produces(MediaType.TEXT_HTML)
    public String hello(@QueryParam("user") @DefaultValue("developer") String user) {
        String message = "";

        String current = Playpen.current();
        if (current != null) {
            message += "<h1> Invoked within a playpen session: " + current + "</h1>";
        }

        String greeting = System.getenv("GREETING_ENV");
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        if (greeting == null) greeting = "Greetings";
        message += "<h1>" + greeting + " " + user + " " + time + "</h1>";
        return message;
    }
}
