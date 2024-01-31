/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

package org.candlepin.pki;

import org.candlepin.model.Consumer;

import java.security.KeyPair;

public interface KeyPairGenerator {

    /**
     * Fetches the key pair for the specified consumer, generating one if necessary.
     * <p><br>
     * If the current key pair is invalid, malformed, or unreadable, a new key pair
     * will be generated instead.
     *
     * @param consumer
     *  the consumer for which to fetch a key pair
     *
     * @throws KeyPairCreationException
     *  When there was a problem during key pair creation.
     *
     * @return
     *  the KeyPair instance containing the public and private keys for the specified consumer
     */
    KeyPair getKeyPair(Consumer consumer);

    /**
     * Generates a new, unassociated key pair consisting of a public and private key.
     *
     * @throws KeyPairCreationException
     *  When there was a problem during key pair creation.
     *
     * @return
     *  a KeyPair instance containing a new public and private key
     */
    KeyPair generateKeyPair();
}
