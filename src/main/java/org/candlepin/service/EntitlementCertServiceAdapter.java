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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.Product;
import org.candlepin.model.Subscription;

/**
 * Interface to the Certificate Service.
 */
public interface EntitlementCertServiceAdapter {

    /**
     * Generate an entitlement certificate, used to grant access to some content.
     *
     * End date specified explicitly to allow for flexible termination policies.
     *
     * @param entitlement entitlement which granted this cert.
     * @param sub Subscription being used.
     * @param product Product being consumed.
     * @return Client entitlement certificate.
     * @throws IOException thrown if there's a problem reading the cert.
     * @throws GeneralSecurityException thrown security problem
     */
    EntitlementCertificate generateEntitlementCert(Entitlement entitlement,
        Subscription sub, Product product)
        throws GeneralSecurityException, IOException;


    /**
     * Generate an ueber certificate, used to grant access to all content for the owner.
     *
     * End date specified explicitly to allow for flexible termination policies.
     *
     * @param entitlement entitlement which granted this cert.
     * @param sub Subscription being used.
     * @param product Product being consumed.
     * @return Client entitlement certificate.
     * @throws IOException thrown if there's a problem reading the cert.
     * @throws GeneralSecurityException thrown security problem
     */
    EntitlementCertificate generateUeberCert(Entitlement entitlement,
        Subscription sub, Product product)
        throws GeneralSecurityException, IOException;

    /**
     * Revoke certificates for the given entitlement
     *
     * @param e Entitlement for which the certificates are going to be revoked
     */
    void revokeEntitlementCertificates(Entitlement e);

    /**
     * Return a list of all entitlement certificates for a given consumer.
     *
     * @param consumer
     * @return All entitlement certs for this consumer.
     */
    List<EntitlementCertificate> listForConsumer(Consumer consumer);
}
