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

import org.candlepin.model.Consumer;
import org.candlepin.model.IdentityCertificate;

/**
 * Interface to the Certificate Service.
 */
public interface IdentityCertServiceAdapter {

    /**
     * Generate an identity certificate, used to verify the identity of the
     * consumer during all future communication between Candlepin and the
     * consumer.
     *
     * @param consumer Consumer.
     * @return the identity certificate for the given consumer.
     * @throws IOException if there is a file system problem
     * @throws GeneralSecurityException if there is a violation of policy
     */
    IdentityCertificate generateIdentityCert(Consumer consumer)
        throws GeneralSecurityException, IOException;

    /**
     * Regenerates the identity certificate for the given consumer.
     *
     * @param consumer Consumer.
     * @return a new identity certificate for the given consumer.
     * @throws IOException if there is a file system problem
     * @throws GeneralSecurityException if there is a violation of policy
     */
    IdentityCertificate regenerateIdentityCert(Consumer consumer)
        throws GeneralSecurityException, IOException;

    /**
     * Deletes the certificate associated with the consumer
     * @param consumer
     */
    void deleteIdentityCert(Consumer consumer);


}
