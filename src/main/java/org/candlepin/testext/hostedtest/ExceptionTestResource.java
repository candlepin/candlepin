/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.testext.hostedtest;

import org.candlepin.exceptions.ConflictException;
import org.candlepin.exceptions.IseException;
import org.candlepin.exceptions.ServiceUnavailableException;
import org.candlepin.exceptions.mappers.ApplicationExceptionMapper;
import org.candlepin.model.Content;

import com.google.inject.Injector;

import org.jboss.resteasy.plugins.providers.jaxb.JAXBMarshalException;
import org.jboss.resteasy.plugins.providers.jaxb.JAXBUnmarshalException;
import org.jboss.resteasy.spi.ApplicationException;
import org.jboss.resteasy.spi.BadRequestException;
import org.jboss.resteasy.spi.DefaultOptionsMethodException;
import org.jboss.resteasy.spi.Failure;
import org.jboss.resteasy.spi.InternalServerErrorException;
import org.jboss.resteasy.spi.NoLogWebApplicationException;
import org.jboss.resteasy.spi.ReaderException;
import org.jboss.resteasy.spi.WriterException;

import jakarta.inject.Inject;
import jakarta.persistence.RollbackException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import jakarta.validation.ValidatorFactory;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAcceptableException;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.NotSupportedException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Controlled failures for exception-mapper spec tests, excluded from production WARs
 */
@Path("/hostedtest/exceptions")
public class ExceptionTestResource {
    private static final String PRIVATE_MESSAGE = "https://internal.example/private secret-token";

    private final Injector injector;

    /**
     * Creates a resource with access to exception-mapper dependencies
     *
     * @param injector
     *     the injector providing exception mappers and validation services
     */
    @Inject
    public ExceptionTestResource(Injector injector) {
        this.injector = injector;
    }

    /**
     * Raises a framework exception with deliberately sensitive diagnostics
     *
     * @param type
     *     the exception type to raise
     * @param status
     *     optional status of an embedded response
     * @return a response only when the framework handles the exception
     */
    @GET
    @Path("/{type}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response fail(@PathParam("type") String type, @QueryParam("status") Integer status) {
        Response response = status != null ? Response.status(status).entity(PRIVATE_MESSAGE).build() : null;
        RuntimeException cause = new IllegalStateException(PRIVATE_MESSAGE);

        if ("application".equals(type)) {
            // This legacy mapper is not registered: RESTEasy normally unwraps ApplicationExceptions.
            return this.injector.getInstance(ApplicationExceptionMapper.class)
                .toResponse(new ApplicationException(PRIVATE_MESSAGE, cause));
        }

        throw switch (type) {
            case "bad-request" -> new BadRequestException(PRIVATE_MESSAGE, cause);
            case "conflict" -> new ConflictException("The requested resource already exists");
            case "constraint-violation" -> this.constraintViolation();
            case "failure" -> new Failure(PRIVATE_MESSAGE, response);
            case "internal-server-error" -> new InternalServerErrorException(PRIVATE_MESSAGE);
            case "ise" -> new IseException(PRIVATE_MESSAGE, cause);
            case "jaxb-marshal" -> new JAXBMarshalException(PRIVATE_MESSAGE, response);
            case "jaxb-unmarshal" -> new JAXBUnmarshalException(PRIVATE_MESSAGE, response);
            case "no-log" -> new NoLogWebApplicationException(cause,
                Response.status(status != null ? status : 400).build());
            case "not-acceptable" -> new NotAcceptableException(PRIVATE_MESSAGE);
            case "not-allowed" -> new NotAllowedException(PRIVATE_MESSAGE,
                Response.status(Response.Status.METHOD_NOT_ALLOWED).allow("GET").build());
            case "not-authorized" -> new NotAuthorizedException(PRIVATE_MESSAGE, "Basic realm=candlepin");
            case "not-found" -> new NotFoundException(PRIVATE_MESSAGE, cause);
            case "not-supported" -> new NotSupportedException(PRIVATE_MESSAGE);
            case "options" -> new DefaultOptionsMethodException(PRIVATE_MESSAGE, response);
            case "options-entity-headers" -> new DefaultOptionsMethodException(PRIVATE_MESSAGE,
                Response.serverError()
                    .entity(PRIVATE_MESSAGE)
                    .type(MediaType.TEXT_PLAIN_TYPE)
                    .header(HttpHeaders.CONTENT_LENGTH, "1")
                    .header(HttpHeaders.CONTENT_ENCODING, "gzip")
                    .header(HttpHeaders.CONTENT_LANGUAGE, "fr")
                    .header(HttpHeaders.ETAG, "private-tag")
                    .header(HttpHeaders.ALLOW, "GET, OPTIONS")
                    .header(HttpHeaders.RETRY_AFTER, "60")
                    .build());
            case "reader" -> new ReaderException(PRIVATE_MESSAGE, response);
            case "rollback" -> new RollbackException(PRIVATE_MESSAGE, cause);
            case "rollback-no-cause" -> new RollbackException(PRIVATE_MESSAGE);
            case "rollback-validation" -> new RollbackException(PRIVATE_MESSAGE,
                new ValidationException(PRIVATE_MESSAGE, cause));
            case "rollback-constraint-violation" -> new RollbackException(PRIVATE_MESSAGE,
                this.constraintViolation());
            case "runtime" -> new IllegalStateException(PRIVATE_MESSAGE, cause);
            case "service-unavailable" -> new ServiceUnavailableException(PRIVATE_MESSAGE, cause)
                .setRetryAfterTime(60);
            case "validation" -> new ValidationException(PRIVATE_MESSAGE, cause);
            case "web-application" -> new WebApplicationException(PRIVATE_MESSAGE,
                Response.status(status != null ? status : 500).build());
            case "writer" -> new WriterException(PRIVATE_MESSAGE, response);
            case "wrapped-ise" -> new RuntimeException(PRIVATE_MESSAGE, new IseException(PRIVATE_MESSAGE, cause));
            default -> new IllegalArgumentException("Unknown test exception: " + type);
        };
    }

    private ConstraintViolationException constraintViolation() {
        return new ConstraintViolationException(PRIVATE_MESSAGE,
            this.injector
                .getInstance(ValidatorFactory.class)
                .getValidator()
                .validateProperty(new Content("test-content"), "name"));
    }
}
