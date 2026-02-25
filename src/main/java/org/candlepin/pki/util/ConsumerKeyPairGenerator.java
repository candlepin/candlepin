/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.pki.util;

import org.candlepin.model.Consumer;
import org.candlepin.model.KeyPairData;
import org.candlepin.model.KeyPairDataCurator;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.Scheme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.GeneralSecurityException;
import java.security.KeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Singleton;



/**
 * Utility class for managing key pairs for consumers.
 */
@Singleton
public class ConsumerKeyPairGenerator {
    private static Logger log = LoggerFactory.getLogger(ConsumerKeyPairGenerator.class);

    /** The algorithm to use when the key pair data does not define it; used only for legacy entries */
    private static final String LEGACY_KEYPAIR_ALGORITHM = "RSA:4096";

    private final CryptoManager cryptoManager;
    private final KeyPairDataCurator keyPairDataCurator;

    @Inject
    public ConsumerKeyPairGenerator(CryptoManager cryptoManager, KeyPairDataCurator keyPairDataCurator) {
        this.cryptoManager = Objects.requireNonNull(cryptoManager);
        this.keyPairDataCurator = Objects.requireNonNull(keyPairDataCurator);
    }

    /**
     * Builds an algorithm string to use for checking if the key pair data was built using the specified
     * scheme. The resultant string will contain the scheme's key algorithm and key size (if present), and
     * will be constructed in a way that is repeatable and deterministic, making it usable as a key or
     * identifier.
     *
     * @param scheme
     *  the scheme from which to build the algorithm string
     *
     * @return
     *  an algorithm string uniquely identifying the scheme's key algorithm and its configuration
     */
    private String buildAlgorithmString(Scheme scheme) {
        return scheme.keySize()
            .map(size -> String.format("%s:%s", scheme.keyAlgorithm(), size))
            .orElseGet(scheme::keyAlgorithm);
    }

    /**
     * Generates a new key pair using the given cryptographic scheme.
     *
     * @param scheme
     *  the scheme to use to generate the key pair
     *
     * @return
     *  a new key pair
     */
    private KeyPair generateKeyPair(Scheme scheme) throws KeyException {
        return this.cryptoManager.getKeyPairGenerator(scheme)
            .generateKeyPair();
    }

    /**
     * Builds a PublicKey instance from the provided key data and algorithm.
     *
     * @param keydata
     *  the X509 encoded public key data to use to construct a PublicKey instance
     *
     * @param algorithm
     *  the algorithm used to build the key
     *
     * @return
     *  a PublicKey instance
     */
    private PublicKey decodePublicKeyData(KeyFactory factory, byte[] keydata)
        throws NoSuchAlgorithmException, InvalidKeySpecException {

        if (keydata == null) {
            throw new InvalidKeySpecException("public key data is null");
        }

        return factory.generatePublic(new X509EncodedKeySpec(keydata));
    }

    /**
     * Builds a PrivateKey instance from the provided key data and algorithm.
     *
     * @param keydata
     *  the PKCS8 private key data to use to construct a PrivateKey instance
     *
     * @param algorithm
     *  the algorithm used to build the key
     *
     * @return
     *  a PrivateKey instance
     */
    private PrivateKey decodePrivateKeyData(KeyFactory factory, byte[] keydata)
        throws NoSuchAlgorithmException, InvalidKeySpecException {

        if (keydata == null) {
            throw new InvalidKeySpecException("private key data is null");
        }

        return factory.generatePrivate(new PKCS8EncodedKeySpec(keydata));
    }

    /**
     * Attempts to process the given key pair data as if the keys are PKCS8 formatted. If the key pair data is
     * incomplete or invalid, this method returns null.
     *
     * @param keyPairData
     *  the key pair data to process; cannot be null, must contain an algorithm string
     *
     * @return
     *  a KeyPair consisting of the keys from the provided key pair data, or null if the key pair
     *  data could not be processed
     */
    private KeyPair processAsPKCS8(KeyPairData keyPairData) {
        try {
            String algoString = keyPairData.getAlgorithm();
            String algorithm = algoString.indexOf(':') != -1 ?
                algoString.substring(0, algoString.indexOf(':')) :
                algoString;

            KeyFactory factory = KeyFactory.getInstance(algorithm, this.cryptoManager.getSecurityProvider());

            PublicKey publicKey = this.decodePublicKeyData(factory, keyPairData.getPublicKeyData());
            PrivateKey privateKey = this.decodePrivateKeyData(factory, keyPairData.getPrivateKeyData());

            return new KeyPair(publicKey, privateKey);
        }
        catch (GeneralSecurityException e) {
            // If any exception occurred, the keys are either malformed or not PKCS8 keys
            log.debug("Unexpected exception occurred while parsing key data: ", e);
            return null;
        }
    }

    /**
     * Fetches the key pair for the specified consumer, generating or regenerating it if necessary. If the
     * key pair requires generation or regeneration, the value stored on the consumer will be updated and
     * persisted.
     * <p>
     * This method will attempt to use the consumer's existing key pair if it has one and it was generated
     * using the same algorithm as defined by the current context scheme for the consumer. If the key pair
     * appears to be malformed or of a different key algorithm, a new key pair will be generated and the
     * consumer will be updated accordingly.
     * <p>
     * <strong>Warning:</strong> This method makes assumptions about the persistence lifecycle of the model
     * entities it handles. Specifically, it assumes that the Consumer, as well as any KeyPairData objects it
     * may contain, are properly managed entities. Modifications to the entities are assumed to be persisted
     * automatically as part of the standard flows for JPA objects, and new KeyPairData instances created as a
     * result of this method's operation will be persisted before being assigned to the consumer. If the
     * consumer itself is not a managed object, it will be up to the caller to ensure the changes made to the
     * consumer or its key pair are persisted.
     *
     * @param consumer
     *  the consumer for which to fetch a key pair
     *
     * @throws IllegalArgumentException
     *  if the given consumer is null
     *
     * @throws KeyException
     *  if an exception occurs while generating (or regenerating) a key pair for the consumer
     *
     * @return
     *  the key pair for the given consumer
     */
    public KeyPair getConsumerKeyPair(Consumer consumer) throws KeyException {
        if (consumer == null) {
            throw new IllegalArgumentException("consumer is null");
        }

        KeyPairData kpdata = consumer.getKeyPairData();

        Scheme scheme = this.cryptoManager.getCryptoScheme(consumer)
            .orElse(this.cryptoManager.getDefaultCryptoScheme());
        String schemeKeyAlgorithm = this.buildAlgorithmString(scheme);

        // Check that the consumer even has a key pair to begin with
        if (kpdata == null) {
            log.debug("Generating new key pair for consumer w/uuid \"{}\"", consumer.getUuid());

            KeyPair keypair = this.generateKeyPair(scheme);

            kpdata = new KeyPairData()
                .setPublicKeyData(keypair.getPublic().getEncoded())
                .setPrivateKeyData(keypair.getPrivate().getEncoded())
                .setAlgorithm(schemeKeyAlgorithm);

            this.keyPairDataCurator.create(kpdata);
            consumer.setKeyPairData(kpdata);

            return keypair;
        }

        // If the algorithm isn't defined in the keypair data, assume it's a legacy keypair and update the
        // kpdata accordingly.
        if (kpdata.getAlgorithm() == null) {
            kpdata.setAlgorithm(LEGACY_KEYPAIR_ALGORITHM);
        }

        // Does the algorithm string match? If not, then it doesn't matter if we can decode the pair: we're
        // going to throw it out anyway.
        if (!schemeKeyAlgorithm.equals(kpdata.getAlgorithm())) {
            log.debug("Generating new key pair for consumer w/uuid \"{}\": algorithm mismatch ({} != {})",
                consumer.getUuid(), schemeKeyAlgorithm, kpdata.getAlgorithm());

            KeyPair keypair = this.generateKeyPair(scheme);

            kpdata.setPublicKeyData(keypair.getPublic().getEncoded())
                .setPrivateKeyData(keypair.getPrivate().getEncoded())
                .setAlgorithm(schemeKeyAlgorithm);

            return keypair;
        }

        // Attempt to decode the key pair data as a pair of PKCS8 blobs
        KeyPair keypair = this.processAsPKCS8(kpdata);
        if (keypair != null) {
            // This is our primary path. We're using the current storage format, and the algorithm lines up.
            return keypair;
        }

        // If we couldn't decode the keypair, we have no idea what format this key pair is stored as (or
        // it has become corrupted, or obsolete). Generate a new key pair for this consumer
        log.debug("Generating new key pair for consumer w/uuid \"{}\": unable to decode cached key pair",
            consumer.getUuid());

        keypair = this.generateKeyPair(scheme);

        // Regardless, at this point we need to update the key pair cache for this consumer so it's
        // stored as a PKCS8 keypair with the correct algorithm string.
        kpdata.setPublicKeyData(keypair.getPublic().getEncoded())
            .setPrivateKeyData(keypair.getPrivate().getEncoded())
            .setAlgorithm(schemeKeyAlgorithm);

        return keypair;
    }

}
