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

import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.CandlepinException;
import org.candlepin.common.exceptions.IseException;
import org.candlepin.common.exceptions.NotAuthorizedException;
import org.candlepin.common.resteasy.auth.AuthUtil;
import org.candlepin.common.resteasy.auth.RestEasyOAuthMessage;
import org.candlepin.gutterball.config.ConfigProperties;

import com.google.inject.Inject;

import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;

/**
 * The OAuthFilter provides authentication using the OAuth protocol.
 * <p/>
 * Any method annotated with the SecurityHole annotation will not be protected by this class, and
 * OAuth as a whole may be disabled by setting the <tt>gutterball.auth.oauth.enable</tt>
 * configuration to false.
 */
@Priority(Priorities.AUTHENTICATION)
public class OAuthFilter implements ContainerRequestFilter {
    protected static final OAuthValidator VALIDATOR = new SimpleOAuthValidator();
    protected static final String SIGNATURE_TYPE = "HMAC-SHA1";

    private static Logger log = LoggerFactory.getLogger(OAuthFilter.class);

    private Configuration config;
    private javax.inject.Provider<I18n> i18nProvider;

    private Map<String, OAuthAccessor> accessors;

    @Inject
    public OAuthFilter(Configuration config, javax.inject.Provider<I18n> i18nProvider) {
        this.config = config;
        this.i18nProvider = i18nProvider;

        this.accessors = new HashMap<String, OAuthAccessor>();

        this.setupAccessors();
        this.setupSigners();
    }

    /**
     * Preprocesses a resource method invocation, verifying that the client is able to access the
     * requested resource/action.
     *
     * @param requestContext
     *  The ContainerRequestContext
     */
    @Override
    public void filter(ContainerRequestContext requestContext) {
        I18n i18n = this.i18nProvider.get();

        HttpRequest request = ResteasyProviderFactory.getContextData(HttpRequest.class);

        if (!AuthUtil.getHeader(request, "Authorization").contains("oauth")) {
            throw new NotAuthorizedException(i18n.tr("No credentials provided"));
        }

        try {
            OAuthMessage requestMessage = new RestEasyOAuthMessage(request);
            OAuthAccessor accessor = this.getAccessor(requestMessage);

            if (accessor == null) {
                throw new NotAuthorizedException(i18n.tr("Invalid OAuth unit or secret"));
            }

            // TODO: This is known to be memory intensive.
            VALIDATOR.validateMessage(requestMessage, accessor);

        }
        catch (OAuthProblemException e) {
            log.debug("OAuth problem", e);

            // XXX: for some reason invalid signature (like bad password) has a
            // status code of 200. make it 401 unauthorized instead.
            if (e.getProblem().equals("signature_invalid")) {
                throw new NotAuthorizedException(i18n.tr("Invalid OAuth unit or secret"));
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

                log.debug(String.format("Creating OAuth consumer \"%s\"", consumer));

                this.accessors.put(consumer, accessor);
            }
        }

        if (this.accessors.size() < 1) {
            log.warn("OAuth is enabled, but no OAuth consumers are defined.");
        }
    }

    /**
     * Override the built in signer for HMAC-SHA1 so that we can see better output.
     */
    protected void setupSigners() {
        OAuthSignatureMethod.registerMethodClass(SIGNATURE_TYPE + OAuthSignatureMethod._ACCESSOR,
            CustomSigner.class);
        OAuthSignatureMethod.registerMethodClass(SIGNATURE_TYPE, CustomSigner.class);
    }
}
