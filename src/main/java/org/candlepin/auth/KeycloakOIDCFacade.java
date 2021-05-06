/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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

package org.candlepin.auth;

import org.candlepin.common.exceptions.CandlepinException;

import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.spi.HttpRequest;
import org.keycloak.adapters.OIDCHttpFacade;
import org.keycloak.adapters.spi.AuthenticationError;
import org.keycloak.adapters.spi.HttpFacade;
import org.keycloak.adapters.spi.LogoutError;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import javax.security.cert.X509Certificate;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

/**
 * Facade to allow the Keycloak classes to interface with RestEasy's HttpRequest and ResponseBuilder.
 *
 * Note that since we are not supporting stateful interactions (e.g. sessions), some methods (e.g.
 * getCookie) are stubbed out.
 */
public class KeycloakOIDCFacade implements HttpFacade {

    protected final HttpRequest requestContext;
    protected final RequestFacade requestFacade = new RequestFacade();
    protected final ResponseFacade responseFacade = new ResponseFacade();

    public KeycloakOIDCFacade(HttpRequest containerRequestContext) {
        this.requestContext = containerRequestContext;
    }

    /**
     * RequestFacade for Keycloak Requests
     */
    protected class RequestFacade implements OIDCHttpFacade.Request {

        @Override
        public String getFirstParam(String param) {
            throw new UnsupportedOperationException("Not supported yet");
        }

        @Override
        public String getMethod() {
            return requestContext.getHttpMethod();
        }

        @Override
        public String getURI() {
            return requestContext.getUri().getRequestUri().toString();
        }

        @Override
        public String getRelativePath() {
            return requestContext.getUri().getPath();
        }

        @Override
        public boolean isSecure() {
            return requestContext.getUri().getAbsolutePath().getScheme().equals("https");
        }

        @Override
        public String getQueryParamValue(String param) {
            MultivaluedMap<String, String> queryParams = requestContext.getUri().getQueryParameters();
            if (queryParams == null) {
                return null;
            }
            return queryParams.getFirst(param);
        }

        @Override
        public Cookie getCookie(String cookieName) {
            throw new UnsupportedOperationException("Not supported yet");

        }

        @Override
        public String getHeader(String name) {
            HttpHeaders httpHeaders = requestContext.getHttpHeaders();
            return httpHeaders.getHeaderString(name);
        }

        @Override
        public List<String> getHeaders(String name) {
            MultivaluedMap<String, String> headers = requestContext.getMutableHeaders();
            return headers.get(name);
        }

        @Override
        public InputStream getInputStream() {
            return getInputStream(false);
        }

        @Override
        public InputStream getInputStream(boolean buffered) {
            return requestContext.getInputStream();
        }

        @Override
        public String getRemoteAddr() {
            return ResteasyContext.getContextData(HttpServletRequest.class).getRemoteAddr();
        }

        @Override
        public void setError(AuthenticationError error) {
            // intentionally left empty
        }

        @Override
        public void setError(LogoutError error) {
            // intentionally left empty
        }
    }

    /**
     * ResponseFacade for Keycloak Responses
     */
    protected class ResponseFacade implements OIDCHttpFacade.Response {

        private ResponseBuilder responseBuilder =
            javax.ws.rs.core.Response.status(204);

        @Override
        public void setStatus(int status) {
            responseBuilder.status(status);
        }

        @Override
        public void addHeader(String name, String value) {
            responseBuilder.header(name, value);
        }

        @Override
        public void setHeader(String name, String value) {
            responseBuilder.header(name, value);
        }

        @Override
        public void resetCookie(String name, String path) {
            // For now doesn't need to be supported
            throw new UnsupportedOperationException("Not supported yet");
        }

        @Override
        public void setCookie(String name, String value, String path, String domain, int maxAge,
            boolean secure, boolean httpOnly) {
            // For now doesn't need to be supported
            throw new UnsupportedOperationException("Not supported yet");
        }

        @Override
        public OutputStream getOutputStream() {
            // For now doesn't need to be supported
            throw new UnsupportedOperationException("Not supported yet");
        }

        @Override
        public void sendError(int code) {
            throw new CandlepinException(Status.fromStatusCode(code),
                "Error during authentication");
        }

        @Override
        public void sendError(int code, String message) {
            throw new CandlepinException(Status.fromStatusCode(code), message);
        }

        @Override
        public void end() {
            // intentionally left empty
        }
    }

    @Override
    public Request getRequest() {
        return requestFacade;
    }

    @Override
    public Response getResponse() {
        return responseFacade;
    }

    @Override
    public X509Certificate[] getCertificateChain() {
        throw new UnsupportedOperationException("Not supported yet");
    }
}
