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
package org.fedoraproject.candlepin.client.model;

import java.security.cert.X509Certificate;

/**
 * The Class ProductCertificate.
 */
public class ProductCertificate extends AbstractCertificate {

    /**
     * Instantiates a new product certificate.
     * @param certificate
     *            the certificate
     */
    public ProductCertificate(final X509Certificate certificate) {
        super(certificate);
    }

    /** The entitlement certificate. */
    private EntitlementCertificate entitlementCertificate;

    /**
     * Checks if is installed.
     * @return true, if is installed
     */
    public final boolean isInstalled() {
        return entitlementCertificate != null;
    }

    /**
     * Gets the entitlement certificate.
     * @return the entitlement certificate
     */
    public final EntitlementCertificate getEntitlementCertificate() {
        return entitlementCertificate;
    }

    /**
     * Sets the entitlement certificate.
     * @param cert
     *            the new entitlement certificate
     */
    public final void setEntitlementCertificate(
            final EntitlementCertificate cert) {
        this.entitlementCertificate = cert;
    }
}
