package org.acme;

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
import java.time.LocalTime;
import java.util.Base64;

@Path("/hello")
public class GreetingResource {
    @GET
    @Produces(MediaType.TEXT_HTML)
    public String hello(@QueryParam("user") @DefaultValue("developer") String user) {
        return "<h1>Hey " + user + " " + LocalTime.now() + "</h1>";
    }
}
