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
import org.candlepin.model.ContentAccessCertificate;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Date;

/**
 * Interface to the Certificate Service.
 *
 * @deprecated
 *  This adapter is not implemented by anyone else and makes use of direct model objects. If the
 *  adapter is still needed in the future, this interface should be redefined to no longer use
 *  Candlepin-internal model objects, and minimize the number of adapter views or DTOs as input
 *  parameters.
 */
@Deprecated
public interface ContentAccessCertServiceAdapter {

    // If we ever have a need for more modes, these should move to a proper enum
    String ENTITLEMENT_ACCESS_MODE = "entitlement";
    String ORG_ENV_ACCESS_MODE = "org_environment";

    String DEFAULT_CONTENT_ACCESS_MODE = ENTITLEMENT_ACCESS_MODE;

    /**
     * Generate an entitlement certificate, used to grant access to some
     * content.
     * End date specified explicitly to allow for flexible termination policies.
     *
     * @return Client entitlement certificates.
     * @throws IOException thrown if there's a problem reading the cert.
     * @throws GeneralSecurityException thrown security problem
     */
    ContentAccessCertificate getCertificate(Consumer consumer) throws GeneralSecurityException, IOException;
    boolean hasCertChangedSince(Consumer consumer, Date date);
    void removeContentAccessCert(Consumer consumer);
}
