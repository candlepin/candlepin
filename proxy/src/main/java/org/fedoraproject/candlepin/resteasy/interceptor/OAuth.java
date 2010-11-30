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
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
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
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.auth.UserPrincipal;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.exceptions.CandlepinException;
import org.fedoraproject.candlepin.exceptions.IseException;
import org.fedoraproject.candlepin.exceptions.NotFoundException;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.service.UserServiceAdapter;
import org.jboss.resteasy.spi.HttpRequest;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;
import com.google.inject.Injector;

/**
 * Uses two legged OAuth. If it succeeds, then it pulls the username off of a
 * headers and creates a principal based only on the username
 */
public class OAuth implements AuthProvider {

    protected static final String HEADER = "cp-user";
    protected static final OAuthValidator VALIDATOR = new SimpleOAuthValidator();
    protected static final String SIGNATURE_TYPE = "HMAC-SHA1";

    private Logger log = Logger.getLogger(OAuth.class);
    private UserServiceAdapter userServiceAdapter;
    private OwnerCurator ownerCurator;
    private Injector injector;
    private I18n i18n;
    private Config config;
    private Map<String, OAuthAccessor> accessors = new HashMap<String, OAuthAccessor>();

    @Inject
    OAuth(UserServiceAdapter userServiceAdapter, OwnerCurator ownerCurator,
        Injector injector, Config config) {
        this.userServiceAdapter = userServiceAdapter;
        this.ownerCurator = ownerCurator;
        this.injector = injector;
        this.config = config;
        i18n = this.injector.getInstance(I18n.class);
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
     * Retrieve a header, or the empty string if it is not there.
     * 
     * @return the header or a blank string (no nils)
     */
    public String getHeader(HttpRequest request, String name) {
        String headerValue = "";
        List<String> header = null;
        for (String key : request.getHttpHeaders().getRequestHeaders().keySet()) {
            if (key.equalsIgnoreCase(name)) {
                header = request.getHttpHeaders().getRequestHeader(key);
                break;
            }
        }
        if (null != header && header.size() > 0) {
            headerValue = header.get(0);
        }
        return headerValue;
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
     * Creates a user principal for a given username
     */
    protected Principal createPrincipal(String username) {
        Owner owner = this.userServiceAdapter.getOwner(username);
        UserPrincipal principal = null;
        if (owner == null) {
            String msg = i18n.tr("No owner found for user {0}", username);
            throw new BadRequestException(msg);
        }
        else {
            owner = lookupOwner(owner);
            List<Role> roles = this.userServiceAdapter.getRoles(username);
            principal = new UserPrincipal(username, owner, roles);
        }
        return principal;
    }

    /**
     * Ensure that an owner exists in the db, and throw an exception if not
     * found.
     */
    protected Owner lookupOwner(Owner owner) {
        Owner o = this.ownerCurator.lookupByKey(owner.getKey());
        if (o == null) {
            if (owner.getKey() == null) {
                throw new NotFoundException(
                    i18n.tr("An owner does not exist for a null org id"));
            }
        }

        return o;
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
