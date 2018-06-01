package org.apache.ignite.ci.web.rest.login;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class ServiceUnauthorizedException extends RuntimeException
        implements ExceptionMapper<ServiceUnauthorizedException> {
    public ServiceUnauthorizedException(String message) {
        super(message);
    }

    @SuppressWarnings("unused")
    public ServiceUnauthorizedException() {
    }

    @Override
    public Response toResponse(ServiceUnauthorizedException exception) {
        return Response.status(424).entity(exception.getMessage())
                .type("text/plain").build();
    }
}