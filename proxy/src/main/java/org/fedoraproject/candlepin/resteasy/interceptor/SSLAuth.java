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

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.auth.ConsumerPrincipal;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.jboss.resteasy.spi.HttpRequest;

import com.google.inject.Inject;

/**
 * SSLAuth
 */
class SSLAuth {
    private static final String CERTIFICATES_ATTR = "javax.servlet.request.X509Certificate";
    private static final String UUID_DN_ATTRIBUTE = "CN";
    
    private static Logger log = Logger.getLogger(SSLAuth.class);

    private ConsumerCurator consumerCurator;

    @Inject
    SSLAuth(ConsumerCurator consumerCurator) {
        this.consumerCurator = consumerCurator;
    }

    Principal getPrincipal(HttpRequest request) {

        X509Certificate[] certs = (X509Certificate[]) request
            .getAttribute(CERTIFICATES_ATTR);

        if (certs == null || certs.length < 1) {
            if (log.isDebugEnabled()) {
                log.debug("no certificate was present to authenticate the client");
            }

            return null;
        }

        // certs is an array of certificates presented by the client
        // with the first one in the array being the certificate of the client itself.
        X509Certificate identityCert = certs[0];

        ConsumerPrincipal principal = createPrincipal(parseUuid(identityCert));
        
        if (log.isDebugEnabled() && principal != null) {
            log.debug("principal created for owner '" +
                principal.getOwner().getDisplayName() +
                "' with consumer '" +
                principal.consumer().getUuid() + "'");
        }
        
        return principal;
    }

    // Pulls the consumer uuid off of the x509 cert.
    private String parseUuid(X509Certificate cert) {
        String dn = cert.getSubjectDN().getName();
        Map<String, String> dnAttributes = new HashMap<String, String>();

        for (String attribute : dn.split(",")) {
            attribute = attribute.trim();
            String[] pair = attribute.split("=");

            dnAttributes.put(pair[0], pair[1]);
        }

        return dnAttributes.get(UUID_DN_ATTRIBUTE);
    }

    private ConsumerPrincipal createPrincipal(String consumerUuid) {
        if (consumerUuid != null) {
            Consumer consumer = this.consumerCurator.lookupByUuid(consumerUuid);

            if (consumer != null) {
                return new ConsumerPrincipal(consumer);
            }
        }

        return null;
    }

}
