package org.fedoraproject.candlepin.resource;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import com.sun.jersey.api.client.ClientResponse.Status;

public class NotFoundException extends WebApplicationException {
    public NotFoundException(String message) {
        super(Response.status(Status.NOT_FOUND)
                .entity(message)
                .type("text/plain")
                .build()
        );
    }
}
