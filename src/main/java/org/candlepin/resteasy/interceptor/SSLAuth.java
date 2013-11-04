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
package org.candlepin.resteasy.interceptor;

import org.candlepin.auth.Principal;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.DeletedConsumerCurator;

import com.google.inject.Inject;

import org.jboss.resteasy.spi.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

/**
 * Pulls the consumer id off off a certificate and creates a principal for that.
 * Remember, certs are easy.
 */
class SSLAuth extends ConsumerAuth {
    private static final String CERTIFICATES_ATTR = "javax.servlet.request.X509Certificate";
    private static final String UUID_DN_ATTRIBUTE = "CN";

    private static Logger log = LoggerFactory.getLogger(SSLAuth.class);

    @Inject
    SSLAuth(ConsumerCurator consumerCurator,
        DeletedConsumerCurator deletedConsumerCurator,
        I18n i18n) {
        super(consumerCurator, deletedConsumerCurator, i18n);
    }

    public Principal getPrincipal(HttpRequest request) {

        X509Certificate[] certs = (X509Certificate[]) request
            .getAttribute(CERTIFICATES_ATTR);

        if (certs == null || certs.length < 1) {
            if (log.isDebugEnabled()) {
                log.debug("no certificate was present to authenticate the client");
            }

            return null;
        }

        // certs is an array of certificates presented by the client
        // with the first one in the array being the certificate of the client
        // itself.
        X509Certificate identityCert = certs[0];

        return createPrincipal(parseUuid(identityCert));
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

}
