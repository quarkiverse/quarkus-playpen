package org.acme;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/goodbye")
public class FarewellResource {
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String goodbye(@QueryParam("user") @DefaultValue("developer") String user) throws Exception {
        String message = "Goodbye " + user;
        return message;
    }
}
