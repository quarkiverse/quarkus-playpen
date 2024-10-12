package org.acme;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/hello")
public class GreetingResource {
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello(@QueryParam("user") @DefaultValue("developer") String user) throws Exception {

        String greeting = System.getenv("GREETING_ENV");
        if (greeting == null)
            greeting = "Greetings";
        String message = "" + greeting + " ZOYO " + user;
        return message;
    }
}
