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
package org.candlepin.model;

import java.security.NoSuchAlgorithmException;

import org.candlepin.pki.PKIUtility;

import com.google.inject.Inject;

/**
 * KeyPairCurator
 */
public class KeyPairCurator extends
    AbstractHibernateCurator<KeyPair> {

    private PKIUtility pki;

    @Inject
    public KeyPairCurator(PKIUtility pki) {
        super(KeyPair.class);
        this.pki = pki;
    }

    /**
     * Lookup the keypair for this consumer. If none exists, a pair will be generated.
     * Returns the java.security.KeyPair, not our internal KeyPair.
     * @return server-wide keypair.
     */
    public java.security.KeyPair getConsumerKeyPair(Consumer c) {
        // Lookup all key pairs, there should only ever be one, so raise exception
        // if multiple exist.
        KeyPair cpKeyPair = c.getKeyPair();
        if (cpKeyPair == null) {
            try {
                java.security.KeyPair newPair = pki.generateNewKeyPair();
                cpKeyPair = new KeyPair(newPair.getPrivate(), newPair.getPublic());
                create(cpKeyPair);
                c.setKeyPair(cpKeyPair);
            }
            catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
        java.security.KeyPair returnMe = new java.security.KeyPair(
            cpKeyPair.getPublicKey(), cpKeyPair.getPrivateKey());
        return returnMe;
    }
}
