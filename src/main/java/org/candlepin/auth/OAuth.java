/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import org.candlepin.config.Configuration;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.CandlepinException;
import org.candlepin.exceptions.IseException;
import org.candlepin.exceptions.NotAuthorizedException;
import org.candlepin.resteasy.filter.AuthUtil;
import org.candlepin.resteasy.filter.RestEasyOAuthMessage;
import org.candlepin.util.Util;

import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthException;
import net.oauth.OAuthMessage;
import net.oauth.OAuthProblemException;
import net.oauth.OAuthValidator;
import net.oauth.SimpleOAuthValidator;
import net.oauth.signature.OAuthSignatureMethod;

import org.jboss.resteasy.spi.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.core.Response;

/**
 * Uses two legged OAuth. If it succeeds, then it pulls the username off of a
 * headers and creates a principal based only on the username
 */
public class OAuth implements AuthProvider {

    private static final Logger log = LoggerFactory.getLogger(OAuth.class);;
    private static final OAuthValidator VALIDATOR = new SimpleOAuthValidator();
    private static final String SIGNATURE_TYPE = "HMAC-SHA1";

    private final Configuration config;
    private final TrustedUserAuth userAuth;
    private final TrustedConsumerAuth consumerAuth;
    private final TrustedExternalSystemAuth systemAuth;
    private final Map<String, OAuthAccessor> accessors = new HashMap<>();
    private final Provider<I18n> i18nProvider;

    @Inject
    OAuth(TrustedConsumerAuth consumerAuth, TrustedUserAuth userAuth,
        TrustedExternalSystemAuth systemAuth, Provider<I18n> i18nProvider, Configuration config) {
        this.config = Objects.requireNonNull(config);
        this.userAuth = Objects.requireNonNull(userAuth);
        this.consumerAuth = Objects.requireNonNull(consumerAuth);
        this.systemAuth = Objects.requireNonNull(systemAuth);
        this.i18nProvider = Objects.requireNonNull(i18nProvider);
        this.setupAccessors();
        this.setupSigners();
    }

    /**
     * Attempt to pull a principal off of an oauth signed message.
     *
     * @return the principal if it can be created, null otherwise
     */
    public Principal getPrincipal(HttpRequest httpRequest) {
        Principal principal = null;
        I18n i18n = i18nProvider.get();
        try {
            if (AuthUtil.getHeader(httpRequest, "Authorization").contains("oauth")) {
                OAuthMessage requestMessage = new RestEasyOAuthMessage(httpRequest);
                OAuthAccessor accessor = this.getAccessor(requestMessage);

                // TODO: This is known to be memory intensive.
                VALIDATOR.validateMessage(requestMessage, accessor);

                // If we got here, it is a valid oauth message.
                // Figure out which kind of principal we should create, based on header
                log.debug("Using OAuth");
                if (!AuthUtil.getHeader(httpRequest, TrustedUserAuth.USER_HEADER).equals("")) {
                    principal = userAuth.getPrincipal(httpRequest);
                }
                else if (!AuthUtil.getHeader(httpRequest, TrustedConsumerAuth.CONSUMER_HEADER).equals("")) {
                    principal = consumerAuth.getPrincipal(httpRequest);
                }
                else {
                    // The external system is acting on behalf of itself
                    principal = systemAuth.getPrincipal(httpRequest);
                }
            }
        }
        catch (OAuthProblemException e) {
            log.debug("OAuth Problem", e);

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
        catch (URISyntaxException | IOException e) {
            throw new IseException(e.getMessage(), e);
        }

        return principal;
    }

    /**
     * Get an oauth accessor for a given message. An exception is thrown if no
     * accessor is found.
     *
     * @param msg
     * @return the OAuth accessor for the given message.
     */
    private OAuthAccessor getAccessor(OAuthMessage msg) {
        I18n i18n = i18nProvider.get();
        try {
            OAuthAccessor accessor = accessors.get(msg.getConsumerKey());

            if (accessor == null) {
                throw new NotAuthorizedException(i18n.tr("Invalid OAuth unit or secret"));
            }

            return accessor;
        }
        catch (IOException e) {
            throw new IseException(i18n.tr("Error getting OAuth unit key", e));
        }

    }

    /**
     * Look for settings which are in the form of
     * candlepin.auth.oauth.consumer.CONSUMERNAME.secret = CONSUMERSECRET and
     * create consumers for them.
     */
    private void setupAccessors() {
        String prefix = "candlepin.auth.oauth.consumer.";
        Map<String, String> oauthConfig = config.getValuesByPrefix(prefix);

        for (Map.Entry<String, String> oauth : oauthConfig.entrySet()) {
            String key = Util.stripPrefix(oauth.getKey(), prefix);
            String[] parts = key.split("\\.");

            if ((parts.length == 2) && (parts[1].equals("secret"))) {
                String consumerName = parts[0];
                String secret = oauth.getValue();
                log.debug("Creating consumer '{}'", consumerName);

                OAuthConsumer consumer = new OAuthConsumer("", consumerName, secret, null);
                OAuthAccessor accessor = new OAuthAccessor(consumer);
                accessors.put(consumerName, accessor);
            }
        }
    }

    /**
     * Override the builtin signer for HMAC-SHA1 so that we can see better
     * output.
     */
    private void setupSigners() {
        log.debug("Add custom signers");
        OAuthSignatureMethod.registerMethodClass(SIGNATURE_TYPE +
            OAuthSignatureMethod._ACCESSOR,
            net.oauth.signature.CustomSigner.class);
        OAuthSignatureMethod.registerMethodClass(SIGNATURE_TYPE,
            net.oauth.signature.CustomSigner.class);
    }
}
