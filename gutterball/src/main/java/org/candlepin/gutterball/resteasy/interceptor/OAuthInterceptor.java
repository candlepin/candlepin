/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.gutterball.resteasy.interceptor;

import org.candlepin.common.auth.SecurityHole;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.CandlepinException;
import org.candlepin.common.exceptions.IseException;
import org.candlepin.common.exceptions.UnauthorizedException;
import org.candlepin.common.resteasy.auth.RestEasyOAuthMessage;
import org.candlepin.gutterball.config.ConfigProperties;

import com.google.inject.Inject;
import com.google.inject.Injector;

import org.jboss.resteasy.annotations.interception.SecurityPrecedence;
import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.ResourceMethod;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.Failure;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.interception.AcceptedByMethod;
import org.jboss.resteasy.spi.interception.PreProcessInterceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.xnap.commons.i18n.I18n;

import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthException;
import net.oauth.OAuthMessage;
import net.oauth.OAuthProblemException;
import net.oauth.OAuthValidator;
import net.oauth.SimpleOAuthValidator;
import net.oauth.signature.CustomSigner;
import net.oauth.signature.OAuthSignatureMethod;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



/**
 * The OAuthInterceptor provides basic authentication using the OAuth protocol by injecting itself
 * into the call chain for registered resource methods.
 * <p/>
 * Any method annotated with the SecurityHole annotation will not be protected by this class, and
 * OAuth as a whole may be disabled by setting the <tt>gutterball.auth.oauth.enable</tt>
 * configuration to false.
 */
@Provider
@ServerInterceptor
@SecurityPrecedence
public class OAuthInterceptor implements PreProcessInterceptor, AcceptedByMethod {
    protected static final OAuthValidator VALIDATOR = new SimpleOAuthValidator();
    protected static final String SIGNATURE_TYPE = "HMAC-SHA1";

    private static Logger log = LoggerFactory.getLogger(OAuthInterceptor.class);

    private Injector injector;
    private Configuration config;
    private javax.inject.Provider<I18n> i18nProvider;

    private Map<String, OAuthAccessor> accessors;


    @Inject
    public OAuthInterceptor(Injector injector, Configuration config,
        javax.inject.Provider<I18n> i18nProvider) {
        super();

        this.injector = injector;
        this.config = config;
        this.i18nProvider = i18nProvider;

        this.accessors = new HashMap<String, OAuthAccessor>();

        this.setupAccessors();
        this.setupSigners();
    }

    /**
     * Checks if this interceptor should be injected into the call chain for the specified method.
     *
     * @param declarer
     *  A Class instance representing the class in which the method is defined.
     *
     * @param method
     *  The method
     *
     * @return
     *  true if this interceptor is to be injected into to method's call chain; false otherwise.
     */
    @Override
    public boolean accept(Class declarer, Method method) {
        if (declarer == null || method == null) {
            return false;
        }

        boolean result = this.config.getBoolean(ConfigProperties.OAUTH_AUTHENTICATION, true);

        // Check if the method defines the security hole annotation
        result = result && (this.getSecurityHole(method) == null);

        if (!result) {
            log.debug("OAuth disabled for method \"" + method.getName() + '"');
        }

        return result;
    }

    /**
     * Preprocesses a resource method invocation, verifying that the client is able to access the
     * requested resource/action.
     *
     * @param request
     *  A HttpRequest instance containing details for the request.
     *
     * @param method
     *  A ResourceMethod instance representing the method to be invoked if the request is accepted.
     *
     * @throws Failure
     *  if Resteasy encounters an internal error.
     *
     * @throws WebApplicationException
     *  if an unexpected error occurs while preprocessing the method invocation.
     *
     * @return
     *  a ServerResponse instance containing the response to send to the client when the request is
     *  to be denied; null if the request is to be accepted.
     */
    @Override
    public ServerResponse preProcess(HttpRequest request, ResourceMethod method) throws Failure,
        WebApplicationException {
        I18n i18n = this.i18nProvider.get();

        if (!this.getHeader(request, "Authorization").contains("oauth")) {
            throw new UnauthorizedException(i18n.tr("No credentials provided"));
        }

        try {
            OAuthMessage requestMessage = new RestEasyOAuthMessage(request);
            OAuthAccessor accessor = this.getAccessor(requestMessage);

            if (accessor == null) {
                throw new UnauthorizedException(i18n.tr("Invalid OAuth unit or secret"));
            }

            // TODO: This is known to be memory intensive.
            VALIDATOR.validateMessage(requestMessage, accessor);

        }
        catch (OAuthProblemException e) {
            log.debug("OAuth problem", e);

            // XXX: for some reason invalid signature (like bad password) has a
            // status code of 200. make it 401 unauthorized instead.
            if (e.getProblem().equals("signature_invalid")) {
                throw new UnauthorizedException(i18n.tr("Invalid OAuth unit or secret"));
            }

            Response.Status returnCode = Response.Status.fromStatusCode(e.getHttpStatusCode());
            String message = i18n.tr("OAuth problem encountered. Internal message is: {0}", e.getMessage());

            throw new CandlepinException(returnCode, message);
        }
        catch (OAuthException e) {
            log.debug("OAuth Error", e);
            String message = i18n.tr("OAuth error encountered. Internal message is: {0}", e.getMessage());

            throw new BadRequestException(message);
        }
        catch (URISyntaxException e) {
            throw new IseException(e.getMessage(), e);
        }
        catch (IOException e) {
            throw new IseException(e.getMessage(), e);
        }

        return null;
    }

    /**
     * Retrieve a header, or the empty string if it is not there.
     *
     * @return the header or a blank string (no nulls)
     */
    protected String getHeader(HttpRequest request, String name) {
        String headerValue = "";

        if (request != null && name != null) {
            List<String> header = null;
            HttpHeaders headers = request.getHttpHeaders();

            for (String key : headers.getRequestHeaders().keySet()) {
                if (key.equalsIgnoreCase(name)) {
                    header = headers.getRequestHeader(key);
                    break;
                }
            }

            if (header != null && header.size() > 0) {
                headerValue = header.get(0);
            }
        }

        return headerValue;
    }

    /**
     * Get an OAuth accessor for a given message. If an appropriate accessor is not found for the
     * message, this method returns null.
     *
     * @param message
     *  The message for which to lookup an OAuth accessor.
     *
     * @throws IllegalArgumentException
     *  if message is null.
     *
     * @throws IseException
     *  if an error occurs while attempting to retrieve an accessor.
     *
     * @return
     *  An OAuthAccessor instance for the specified message, or null if an appropriate accessor
     *  could not be found.
     */
    protected OAuthAccessor getAccessor(OAuthMessage message) {
        I18n i18n = this.i18nProvider.get();

        if (message == null) {
            throw new IllegalArgumentException(i18n.tr("message is null"));
        }

        try {
            return accessors.get(message.getConsumerKey());
        }
        catch (IOException e) {
            throw new IseException(i18n.tr("Error getting OAuth unit key", e));
        }
    }

    /**
     * Retrieves a SecurityHole instance for the specified method. If the method is not a security
     * hole (that is, it requires authentication), this method returns null. If the method has
     * multiple security hole definitions, this method returns the first one found.
     *
     * @param method
     *  The method for which to retrieve a SecurityHole instance.
     *
     * @return
     *  a SecurityHole instance for the specified method; or null if the method requires
     *  authentication.
     */
    protected SecurityHole getSecurityHole(Method method) {
        if (method != null) {
            for (Annotation annotation : method.getAnnotations()) {
                if (annotation instanceof SecurityHole) {
                    return (SecurityHole) annotation;
                }
            }
        }

        return null;
    }

    /**
     * Look for settings which are in the form of
     * gutterball.auth.oauth.consumer.CONSUMERNAME.secret = CONSUMERSECRET and
     * create consumers for them.
     */
    protected void setupAccessors() {
        Pattern pattern = Pattern.compile(ConfigProperties.OAUTH_CONSUMER_REGEX);
        Matcher matcher;

        for (String key : this.config.getKeys()) {
            matcher = pattern.matcher(key);

            if (matcher.matches() && matcher.groupCount() >= 1) {
                String consumer = matcher.group(1);
                String secret = this.config.getProperty(key);
                OAuthAccessor accessor = new OAuthAccessor(new OAuthConsumer("", consumer, secret, null));

                log.debug(String.format("Creating OAuth consumer \"%s\" with secret \"%s\"",
                    consumer, secret));

                this.accessors.put(consumer, accessor);
            }
        }
    }

    /**
     * Override the built in signer for HMAC-SHA1 so that we can see better output.
     */
    protected void setupSigners() {
        log.debug("Add custom signers");
        OAuthSignatureMethod.registerMethodClass(SIGNATURE_TYPE + OAuthSignatureMethod._ACCESSOR,
            CustomSigner.class);
        OAuthSignatureMethod.registerMethodClass(SIGNATURE_TYPE, CustomSigner.class);
    }
}
