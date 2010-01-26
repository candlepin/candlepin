package org.fedoraproject.candlepin.resource;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import com.sun.jersey.api.client.ClientResponse.Status;

public class BadRequestException extends WebApplicationException {
    public BadRequestException(String message) {
        super(Response.status(Status.BAD_REQUEST)
                .entity(message)
                .type("text/plain")
                .build()
        );
    }
}
