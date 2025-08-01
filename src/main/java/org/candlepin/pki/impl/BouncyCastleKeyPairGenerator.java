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
package org.candlepin.pki.impl;

import org.bouncycastle.jcajce.spec.MLDSAParameterSpec;
import org.candlepin.model.Consumer;
import org.candlepin.model.KeyPairData;
import org.candlepin.model.KeyPairDataCurator;
import org.candlepin.pki.KeyPairCreationException;
import org.candlepin.pki.KeyPairGenerator;

import com.google.inject.Inject;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Objects;

import javax.inject.Provider;

/**
 * Class handles creation of {@link KeyPair}.
 */
public class BouncyCastleKeyPairGenerator implements KeyPairGenerator {
    private static final Logger log = LoggerFactory.getLogger(BouncyCastleKeyPairGenerator.class);
    private static final String KEY_ALGORITHM = "RSA";
    private static final int KEY_SIZE = 4096;

    private final Provider<BouncyCastleProvider> securityProvider;
    private final KeyPairDataCurator keypairDataCurator;

    @Inject
    public BouncyCastleKeyPairGenerator(Provider<BouncyCastleProvider> securityProvider,
        KeyPairDataCurator keypairDataCurator) {
        this.keypairDataCurator = Objects.requireNonNull(keypairDataCurator);
        this.securityProvider = Objects.requireNonNull(securityProvider);
    }

    @Override
    public KeyPair getKeyPair(Consumer consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("consumer is null");
        }

        KeyPairData kpdata = consumer.getKeyPairData();
        KeyPair keypair = null;

        if (kpdata == null) {
            // no key data, create new and persist
            keypair = this.generateKeyPair();

            kpdata = new KeyPairData()
                .setPublicKeyData(keypair.getPublic().getEncoded())
                .setPrivateKeyData(keypair.getPrivate().getEncoded());

            kpdata = this.keypairDataCurator.create(kpdata, false);
            consumer.setKeyPairData(kpdata);
        }
        else {
            // Try to process as PKCS8 data
            keypair = this.processAsPKCS8(kpdata);

            // If output is null, it's not PKCS8 data, try to process it as a Java-serialized object
            if (keypair == null) {
                log.info("Key pair does not appear to be PKCS8 data; attempting Java deserialization...");
                keypair = this.processAsJSO(kpdata);

                // If output is still null here, the key is malformed, so we should generate
                // a new one
                if (keypair == null) {
                    log.warn("Malformed key data found for consumer {}, generating new key pair",
                        consumer.getUuid());
                    keypair = this.generateKeyPair();
                }

                // In either case, we need to update the key pair data associated with the
                // consumer, so we can avoid this conversion in the future.
                kpdata.setPublicKeyData(keypair.getPublic().getEncoded());
                kpdata.setPrivateKeyData(keypair.getPrivate().getEncoded());

                kpdata = this.keypairDataCurator.merge(kpdata);
                consumer.setKeyPairData(kpdata);
            }
        }

        return keypair;
    }

    @Override
    public KeyPair generateKeyPair() {
        try {
            java.security.KeyPairGenerator keyGen = null;
            try {
                keyGen = java.security.KeyPairGenerator.getInstance("MLDSA", "BC");
                keyGen.initialize(MLDSAParameterSpec.ml_dsa_65);
            } catch (NoSuchProviderException e) {
                throw new RuntimeException(e);
            } catch (InvalidAlgorithmParameterException e) {
                throw new RuntimeException(e);
            }

            return keyGen.generateKeyPair();
        }
        catch (NoSuchAlgorithmException e) {
            throw new KeyPairCreationException("Failed to generate a new key pair!", e);
        }
    }

    /**
     * Attempts to process the given keypair data as if the keys are PKCS8 formatted. If the key
     * pair data is incomplete or invalid, this method returns null.
     *
     * @param keyPairData
     *  the key pair data to process
     *
     * @return
     *  a KeyPair consisting of the keys from the provided key pair data, or null if the key pair
     *  data could not be processed
     */
    private KeyPair processAsPKCS8(KeyPairData keyPairData) {
        try {
            PublicKey publicKey = this.generatePublicKey(keyPairData.getPublicKeyData(), KEY_ALGORITHM);
            PrivateKey privateKey = this.generatePrivateKey(keyPairData.getPrivateKeyData(), KEY_ALGORITHM);

            return new KeyPair(publicKey, privateKey);
        }
        catch (GeneralSecurityException e) {
            // If any exception occurred, the keys are either malformed or not PKCS8 keys
            log.debug("Unexpected exception occurred while parsing key data: ", e);
            return null;
        }
    }

    /**
     * Attempts to process the given keypair data as if the keys are Java-serialized key objects. If
     * the key pair data is incomplete or invalid, this method returns null.
     *
     * @param keyPairData
     *  the key pair data to process
     *
     * @return
     *  a KeyPair consisting of the keys from the provided key pair data, or null if the key pair
     *  data could not be processed
     */
    private KeyPair processAsJSO(KeyPairData keyPairData) {
        try {
            PublicKey publicKey = this.deserializeKey(keyPairData.getPublicKeyData(), PublicKey.class);
            PrivateKey privateKey = this.deserializeKey(keyPairData.getPrivateKeyData(), PrivateKey.class);

            return new KeyPair(publicKey, privateKey);
        }
        catch (ClassNotFoundException | IOException e) {
            // If any exception occurred, the keys are either malformed, not Java-serialized key
            // objects, or something that doesn't allow extracting key data
            log.debug("Unexpected exception occurred while deserializing key data: ", e);
            return null;
        }
    }

    /**
     * Deserializes the given byte array into an object of the specified class. If the data provided
     * cannot be deserialized into the given class, this method throws an exception.
     *
     * @param bytes
     *  the data to deserialize
     *
     * @param keyClass
     *  the key class to which the data should be deserialized
     *
     * @throws IOException
     *  if the key data is missing
     *
     * @throws KeyPairCreationException
     *  if the key data is malformed, invalid, or otherwise cannot be deserialized to the specified
     *  class
     *
     * @return
     *  an instance of the given key class representing the key data provided
     */
    private <T extends Key> T deserializeKey(byte[] bytes, Class<T> keyClass)
        throws ClassNotFoundException, IOException {

        if (bytes == null) {
            throw new IOException("no data to deserialize");
        }

        try (ObjectInputStream ostream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object obj = ostream.readObject();

            if (!keyClass.isInstance(obj)) {
                throw new KeyPairCreationException(
                    "Incorrect object parsed from key data: " + obj.getClass());
            }

            return (T) obj;
        }
    }

    /**
     * Generates a PublicKey instance from the provided key data and algorithm.
     *
     * @param keydata
     *  the X509 encoded public key data from which to generate a PublicKey instance
     *
     * @param algorithm
     *  the algorithm used to generate the key
     *
     * @return
     *  a PublicKey instance
     */
    private PublicKey generatePublicKey(byte[] keydata, String algorithm)
        throws NoSuchAlgorithmException, InvalidKeySpecException {

        KeyFactory factory = KeyFactory.getInstance(algorithm, this.securityProvider.get());
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keydata, algorithm);

        return factory.generatePublic(spec);
    }

    /**
     * Generates a PrivateKey instance from the provided key data and algorithm.
     *
     * @param keydata
     *  the PKCS8 private key data from which to generate a PrivateKey instance
     *
     * @param algorithm
     *  the algorithm used to generate the key
     *
     * @return
     *  a PrivateKey instance
     */
    private PrivateKey generatePrivateKey(byte[] keydata, String algorithm)
        throws NoSuchAlgorithmException, InvalidKeySpecException {

        KeyFactory factory = KeyFactory.getInstance(algorithm, this.securityProvider.get());
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keydata, algorithm);

        return factory.generatePrivate(spec);
    }
}
