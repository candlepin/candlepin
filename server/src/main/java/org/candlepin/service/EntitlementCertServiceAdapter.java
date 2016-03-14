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
package org.candlepin.service;

import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.Product;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;

/**
 * Interface to the Certificate Service.
 */
public interface EntitlementCertServiceAdapter {

    /**
     * Generate an entitlement certificate, used to grant access to some
     * content.
     * End date specified explicitly to allow for flexible termination policies.
     *
     * @param entitlement entitlement which granted the cert.
     * @param product The Products being consumed.
     * @return Client entitlement certificates.
     * @throws IOException thrown if there's a problem reading the cert.
     * @throws GeneralSecurityException thrown security problem
     */
    EntitlementCertificate generateEntitlementCert(Entitlement entitlement, Product product)
        throws GeneralSecurityException, IOException;

    /**
     * Generate an ueber certificate, used to grant access to all content for
     * the owner.
     * End date specified explicitly to allow for flexible termination policies.
     *
     * @param entitlement entitlement which granted this cert.
     * @param product product being consumed.
     * @return Client entitlement certificate.
     * @throws IOException thrown if there's a problem reading the cert.
     * @throws GeneralSecurityException thrown security problem
     */
    EntitlementCertificate generateUeberCert(Entitlement entitlement, Product product)
        throws GeneralSecurityException, IOException;

    /**
     * Generate an entitlement certificate, used to grant access to some
     * content.
     * End date specified explicitly to allow for flexible termination policies.
     * The Map keys are used to associate the entitlement with its product and
     * cert generated. While they do not have to be pool ids, and can be any
     * String, we use pool ids for consistency
     *
     * @param consumer
     * @param entitlements entitlements which granted the certs.
     * @param products The Products being consumed.
     * @return Client entitlement certificates.
     * @throws IOException thrown if there's a problem reading the cert.
     * @throws GeneralSecurityException thrown security problem
     */
    Map<String, EntitlementCertificate> generateEntitlementCerts(Consumer consumer,
            Map<String, Entitlement> entitlements, Map<String, Product> products)
        throws GeneralSecurityException, IOException;

    /**
     * Generate an ueber certificate, used to grant access to all content for
     * the owner.
     * End date specified explicitly to allow for flexible termination policies.
     * The Map keys are used to associate the entitlement with its product and
     * cert generated. While they do not have to be pool ids, and can be any
     * String, we use pool ids for consistency
     *
     * @param consumer
     * @param entitlements entitlement which granted this cert.
     * @param products Product being consumed.
     * @return Client entitlement certificate.
     * @throws IOException thrown if there's a problem reading the cert.
     * @throws GeneralSecurityException thrown security problem
     */
    Map<String, EntitlementCertificate> generateUeberCerts(Consumer consumer,
            Map<String, Entitlement> entitlements, Map<String, Product> products)
        throws GeneralSecurityException, IOException;

    /**
     * Return a list of all entitlement certificates for a given consumer.
     *
     * @param consumer
     * @return All entitlement certs for this consumer.
     */
    List<EntitlementCertificate> listForConsumer(Consumer consumer);
}
