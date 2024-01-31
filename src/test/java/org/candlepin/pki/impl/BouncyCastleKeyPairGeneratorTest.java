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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;

import org.candlepin.model.Consumer;
import org.candlepin.model.KeyPairData;
import org.candlepin.model.KeyPairDataCurator;
import org.candlepin.pki.KeyPairGenerator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;

class BouncyCastleKeyPairGeneratorTest {
    private KeyPairDataCurator keypairCurator;
    private BouncyCastleSecurityProvider securityProvider;

    @BeforeEach
    void setUp() {
        this.keypairCurator = Mockito.mock(KeyPairDataCurator.class);
        this.securityProvider = new BouncyCastleSecurityProvider();
        doAnswer(returnsFirstArg()).when(this.keypairCurator).merge(any());
        doAnswer(returnsFirstArg()).when(this.keypairCurator).create(any());
        doAnswer(returnsFirstArg()).when(this.keypairCurator).create(any(), anyBoolean());
    }

    @Test
    public void testGenerateKeyPair() {
        KeyPairGenerator generator = new BouncyCastleKeyPairGenerator(
            this.securityProvider, this.keypairCurator);
        KeyPair keypair = generator.generateKeyPair();
        assertNotNull(keypair);

        // The keys returned in the key pair *must* be extractable
        PublicKey publicKey = keypair.getPublic();
        assertNotNull(publicKey);
        assertNotNull(publicKey.getFormat());
        assertNotNull(publicKey.getEncoded());

        PrivateKey privateKey = keypair.getPrivate();
        assertNotNull(privateKey);
        assertNotNull(privateKey.getFormat());
        assertNotNull(privateKey.getEncoded());
    }

    @Test
    public void testGetConsumerKeyPair() {
        KeyPairGenerator generator = new BouncyCastleKeyPairGenerator(
            this.securityProvider, this.keypairCurator);
        Consumer consumer = new Consumer();
        assertNull(consumer.getKeyPairData());

        KeyPair keypair = generator.getKeyPair(consumer);
        assertNotNull(keypair);

        // The keys returned in the key pair *must* be extractable
        PublicKey publicKey = keypair.getPublic();
        assertNotNull(publicKey);
        assertNotNull(publicKey.getFormat());
        assertNotNull(publicKey.getEncoded());

        PrivateKey privateKey = keypair.getPrivate();
        assertNotNull(privateKey);
        assertNotNull(privateKey.getFormat());
        assertNotNull(privateKey.getEncoded());

        // The encoding of the returned keys should match what we store in the consumer
        KeyPairData kpdata = consumer.getKeyPairData();
        assertNotNull(kpdata);
        assertArrayEquals(privateKey.getEncoded(), kpdata.getPrivateKeyData());
    }

    @Test
    public void testGetConsumerKeyPairRepeatsOutputForConsumer() {
        KeyPairGenerator generator = new BouncyCastleKeyPairGenerator(
            this.securityProvider, this.keypairCurator);
        Consumer consumer = new Consumer();
        assertNull(consumer.getKeyPairData());

        KeyPair keypairA = generator.getKeyPair(consumer);
        assertNotNull(keypairA);
        assertNotNull(keypairA.getPublic());
        assertNotNull(keypairA.getPrivate());

        KeyPairData kpdataA = consumer.getKeyPairData();
        assertNotNull(kpdataA);

        KeyPair keypairB = generator.getKeyPair(consumer);
        assertNotNull(keypairB);
        assertNotNull(keypairB.getPublic());
        assertNotNull(keypairB.getPrivate());

        KeyPairData kpdataB = consumer.getKeyPairData();
        assertNotNull(kpdataB);

        assertNotSame(keypairA, keypairB);

        // The keypair coming out should be the same, since it shouldn't have required
        // regeneration
        assertArrayEquals(keypairA.getPublic().getEncoded(), keypairB.getPublic().getEncoded());
        assertArrayEquals(keypairA.getPrivate().getEncoded(), keypairB.getPrivate().getEncoded());
    }

    @Test
    public void testGetConsumerKeyPairConvertsLegacySerializedKeyPairs() throws Exception {
        KeyPairGenerator generator = new BouncyCastleKeyPairGenerator(
            this.securityProvider, this.keypairCurator);
        KeyPair keypair = generator.generateKeyPair();
        byte[] serializedPublicKey = this.serializeObject(keypair.getPublic());
        byte[] serializedPrivateKey = this.serializeObject(keypair.getPrivate());

        KeyPairData kpdata = new KeyPairData()
            .setPublicKeyData(serializedPublicKey)
            .setPrivateKeyData(serializedPrivateKey);

        Consumer consumer = new Consumer()
            .setKeyPairData(kpdata);

        KeyPair converted = generator.getKeyPair(consumer);
        assertNotNull(converted);

        // The key pair data should differ, since it should be converted to the newer format,
        // but the keys themselves should remain unchanged
        kpdata = consumer.getKeyPairData();
        assertNotNull(kpdata);
        assertFalse(Arrays.equals(serializedPublicKey, kpdata.getPublicKeyData()));
        assertFalse(Arrays.equals(serializedPrivateKey, kpdata.getPrivateKeyData()));
        assertArrayEquals(keypair.getPublic().getEncoded(), converted.getPublic().getEncoded());
        assertArrayEquals(keypair.getPrivate().getEncoded(), converted.getPrivate().getEncoded());

        // The converted key pair data should match the encoding of the keys
        assertArrayEquals(keypair.getPublic().getEncoded(), kpdata.getPublicKeyData());
        assertArrayEquals(keypair.getPrivate().getEncoded(), kpdata.getPrivateKeyData());
    }

    @Test
    public void testGetConsumerKeyPairRegeneratesMalformedKeyPairs() {
        KeyPairGenerator generator = new BouncyCastleKeyPairGenerator(
            this.securityProvider, this.keypairCurator);
        byte[] publicKeyBytes = "bad_public_key".getBytes();
        byte[] privateKeyBytes = "bad_private_key".getBytes();

        KeyPairData kpdata = new KeyPairData()
            .setPublicKeyData(publicKeyBytes)
            .setPrivateKeyData(privateKeyBytes);

        Consumer consumer = new Consumer()
            .setKeyPairData(kpdata);

        KeyPair keypair = generator.getKeyPair(consumer);
        assertNotNull(keypair);
        assertNotNull(keypair.getPublic());
        assertNotNull(keypair.getPrivate());

        // Ensure the kpdata for this consumer has been updated to match the newly
        // generated key pair
        kpdata = consumer.getKeyPairData();
        assertNotNull(kpdata);
        assertFalse(Arrays.equals(publicKeyBytes, kpdata.getPublicKeyData()));
        assertFalse(Arrays.equals(privateKeyBytes, kpdata.getPrivateKeyData()));

        assertArrayEquals(keypair.getPublic().getEncoded(), kpdata.getPublicKeyData());
        assertArrayEquals(keypair.getPrivate().getEncoded(), kpdata.getPrivateKeyData());
    }

    private byte[] serializeObject(Object key) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream ostream = new ObjectOutputStream(baos)
        ) {
            ostream.writeObject(key);
            return baos.toByteArray();
        }
    }
}
