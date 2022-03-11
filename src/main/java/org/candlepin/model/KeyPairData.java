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

import org.hibernate.annotations.GenericGenerator;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;



/**
 * Container object for public and private key data. The keys are stored using a binary format
 * determined by whichever security provider was responsible for generating the keys, but may be
 * Java-serialized PrivateKey or PublicKey instances from legacy Candlepin versions.
 *
 * New KeyPairData instances and crypto backends should strive to use DER-formatted PKCS8 for
 * storing key pair data, but this is not a strict requirement nor guarantee.
 */
@Entity
@Table(name = KeyPairData.DB_TABLE)
public class KeyPairData extends AbstractHibernateObject<KeyPairData> {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_key_pair";

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    private byte[] privateKey;
    private byte[] publicKey;

    public KeyPairData() {
        // Intentionally left empty
    }

    public String getId() {
        return id;
    }

    public KeyPairData setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Fetches the data representing the private key for this key pair, or null if the private key
     * data has not been set for this key pair.
     * <p></p>
     * <strong>Note</strong>: Legacy implementations of the key data may have a Java-serialized
     * private key instead. This format is no longer used during creation, but may still exist in
     * the database.
     *
     * @return
     *  the data representing the private key of this key pair
     */
    public byte[] getPrivateKeyData() {
        return this.privateKey;
    }

    /**
     * Sets or clears the data representing the private key of this key pair.
     *
     * @param keydata
     *  the data representing the private key, or null to clear any existing key data
     *
     * @return
     *  a reference to this KeyPairData instance
     */
    public KeyPairData setPrivateKeyData(byte[] keydata) {
        this.privateKey = keydata;
        return this;
    }

    /**
     * Fetches the data representing the public key for this key pair, or null if the public key
     * data has not been set for this key pair.
     * <p></p>
     * <strong>Note</strong>: Legacy implementations of the key data may have a Java-serialized
     * public key instead. This format is no longer used during creation, but may still exist in
     * the database.
     *
     * @return
     *  the data representing the public key of this key pair
     */
    public byte[] getPublicKeyData() {
        return this.publicKey;
    }

    /**
     * Sets or clears the data representing the public key of this key pair.
     *
     * @param keydata
     *  the data representing the public key, or null to clear any existing key data
     *
     * @return
     *  a reference to this KeyPairData instance
     */
    public KeyPairData setPublicKeyData(byte[] keydata) {
        this.publicKey = keydata;
        return this;
    }
}
