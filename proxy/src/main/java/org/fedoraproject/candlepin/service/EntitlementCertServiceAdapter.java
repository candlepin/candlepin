/**
 * Copyright (c) 2010 Red Hat, Inc.
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
package org.fedoraproject.candlepin.service;

import java.util.Date;

import org.fedoraproject.candlepin.model.ClientCertificateStatus;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Subscription;

/**
 * Interface to the Certificate Service.
 */
public interface EntitlementCertServiceAdapter {

    /**
     * Generate an entitlement certificate, used to grant access to some content.
     *
     * End date specified explicitly to allow for flexible termination policies.
     *
     * @param consumer Consumer certificate is for.
     * @param sub Subscription being used.
     * @param product Product being consumed.
     * @param endDate End date. (usually subscription end date, but not always)
     * @return Client entitlement certificate.
     */
    ClientCertificateStatus generateEntitlementCert(Consumer consumer,
        Subscription sub, Product product, Date endDate);
}