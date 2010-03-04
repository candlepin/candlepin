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


import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerIdentityCertificate;

/**
 * Interface to the Certificate Service.
 */
public interface IdentityCertServiceAdapter {

    /**
     * Generate an identity certificate, used to verify the identity of the consumer during
     * all future communication between Candlepin and the consumer.
     * @param consumer Consumer.
     * @return consumer's identity certificate.
     */
    ConsumerIdentityCertificate generateIdentityCert(Consumer consumer);
}