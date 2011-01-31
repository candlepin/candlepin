/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.resteasy.interceptor;

import java.io.IOException;
import com.google.inject.Injector;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.ws.rs.core.Response;

import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthException;
import net.oauth.OAuthMessage;
import net.oauth.OAuthProblemException;
import net.oauth.OAuthValidator;
import net.oauth.SimpleOAuthValidator;
import net.oauth.signature.OAuthSignatureMethod;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.exceptions.CandlepinException;
import org.fedoraproject.candlepin.exceptions.IseException;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.service.UserServiceAdapter;
import org.jboss.resteasy.spi.HttpRequest;

import com.google.inject.Inject;

/**
 * Uses two legged OAuth. If it succeeds, then it pulls the username off of a
 * headers and creates a principal based only on the username
 */
public class OAuth extends UserAuth {

    protected static final String HEADER = "cp-user";
    protected static final OAuthValidator VALIDATOR = new SimpleOAuthValidator();
    protected static final String SIGNATURE_TYPE = "HMAC-SHA1";

    private Logger log = Logger.getLogger(OAuth.class);;
    private Config config;
    private Map<String, OAuthAccessor> accessors = new HashMap<String, OAuthAccessor>();

    @Inject
    OAuth(UserServiceAdapter userServiceAdapter, OwnerCurator ownerCurator,
        Injector injector, Config config) {
        super(userServiceAdapter, ownerCurator, injector);
        this.config = config;
        this.setupAccessors();
        this.setupSigners();
    }

    /**
     * Attempt to pull a principal off of an oauth signed message.
     * 
     * @return the principal if it can be created, nil otherwise
     */
    public Principal getPrincipal(HttpRequest request) {
        Principal principal = null;

        log.debug("Checking for oauth authentication");
        try {
            if (getHeader(request, "Authorization").contains("oauth")) {
                OAuthMessage requestMessage = new RestEasyOAuthMessage(request);
                OAuthAccessor accessor = this.getAccessor(requestMessage);

                // TODO: This is known to be memory intensive.
                VALIDATOR.validateMessage(requestMessage, accessor);

                // If we got here, it is a valid oauth message
                String username = getHeader(request, HEADER);

                if ((username == null) || (username.equals(""))) {
                    String msg = i18n
                        .tr("No username provided for oauth request");
                    throw new BadRequestException(msg);
                }

                principal = createPrincipal(username);
                if (log.isDebugEnabled()) {
                    log.debug("principal created for owner '" +
                        principal.getOwner().getDisplayName() +
                        "' with username '" + username + "'");
                }
            }
        }
        catch (OAuthProblemException e) {
            e.printStackTrace();
            Response.Status returnCode = Response.Status.fromStatusCode(e
                .getHttpStatusCode());
            throw new CandlepinException(returnCode, e.getMessage());
        }
        catch (OAuthException e) {
            e.printStackTrace();
            throw new BadRequestException(e.getMessage());
        }
        catch (URISyntaxException e) {
            throw new IseException(e.getMessage(), e);
        }
        catch (IOException e) {
            throw new IseException(e.getMessage(), e);
        }

        return principal;
    }

    /**
     * Get an oauth accessor for a given message. An exception is thrown if no
     * accessor is found.
     * 
     * @param msg
     * @return
     */
    protected OAuthAccessor getAccessor(OAuthMessage msg) {
        try {
            OAuthAccessor accessor = accessors.get(msg.getConsumerKey());
            if (accessor == null) {
                throw new BadRequestException(
                    i18n.tr("No oauth consumer found for key {0}",
                        msg.getConsumerKey()));
            }
            return accessor;
        }
        catch (IOException e) {
            throw new IseException(i18n.tr("Error getting oauth consumer Key",
                e));
        }

    }

    /**
     * Look for settings which are in the form of
     * candlepin.auth.oauth.consumer.CONSUMERNAME.secret = CONSUMERSECRET and
     * create consumers for them.
     */
    protected void setupAccessors() {
        String prefix = "candlepin.auth.oauth.consumer";
        Properties props = config.getNamespaceProperties(prefix);
        for (Entry<Object, Object> entry : props.entrySet()) {
            String key = (String) entry.getKey();
            key = key.replace(prefix + ".", "");
            String[] parts = key.split("\\.");
            if ((parts.length == 2) && (parts[1].equals("secret"))) {
                String consumerName = parts[0];
                String sekret = (String) entry.getValue();
                log.debug(String.format(
                    "Creating consumer '%s' with secret '%s'", consumerName,
                    sekret));
                OAuthConsumer consumer = new OAuthConsumer("", consumerName,
                    sekret, null);
                OAuthAccessor accessor = new OAuthAccessor(consumer);
                accessors.put(consumerName, accessor);
            }
        }
    }

    /**
     * Override the built in signer for HMAC-SHA1 so that we can see better
     * output.
     */
    protected void setupSigners() {
        log.debug("Add custom signers");
        OAuthSignatureMethod.registerMethodClass(SIGNATURE_TYPE +
            OAuthSignatureMethod._ACCESSOR,
            net.oauth.signature.CustomSigner.class);
        OAuthSignatureMethod.registerMethodClass(SIGNATURE_TYPE,
            net.oauth.signature.CustomSigner.class);
    }
}
