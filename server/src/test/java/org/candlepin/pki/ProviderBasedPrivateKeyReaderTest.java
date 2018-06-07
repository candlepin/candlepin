/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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

import static org.junit.Assert.*;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.jcajce.JcePKCSPBEInputDecryptorProviderBuilder;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.security.PrivateKey;
import java.security.Provider;
import java.util.Arrays;

/**
 * Test ProviderBasedPrivateKeyReader
 */
@RunWith(Parameterized.class)
public class ProviderBasedPrivateKeyReaderTest {
    private static final Provider BC_PROVIDER = new BouncyCastleProvider();
    private static final char[] PASSWORD = "password".toCharArray();

    private ClassLoader cl;

    private final Constructor<? extends ProviderBasedPrivateKeyReader> constructor;

    @Before
    public void setUp() {
        cl = ProviderBasedPrivateKeyReaderTest.class.getClassLoader();
    }

    @Parameterized.Parameters
    public static Iterable<Class> data() {
        return Arrays.asList(JSSPrivateKeyReader.class);
    }

    public ProviderBasedPrivateKeyReaderTest(Class<? extends ProviderBasedPrivateKeyReader> clazz)
        throws Exception {
        this.constructor = clazz.getConstructor();
    }

    @Test
    public void testReadUnencryptedPKCS8() throws Exception {
        String keyFile = "keys/pkcs8-unencrypted.pem";
        try (
            InputStream keyStream = cl.getResourceAsStream(keyFile);
            Reader expectedReader = new InputStreamReader(cl.getResourceAsStream(keyFile));
        ) {
            PrivateKey actualKey = constructor.newInstance().read(keyStream, null);
            PrivateKeyInfo expected = (PrivateKeyInfo) new PEMParser(expectedReader).readObject();
            PrivateKey expectedKey = new JcaPEMKeyConverter()
                .setProvider(BC_PROVIDER)
                .getPrivateKey(expected);
            assertEquals(actualKey, expectedKey);
        }
    }

    /**
     * Currently fails due to a bug in OpenJDK: https://bugs.openjdk.java.net/browse/JDK-8076999
     */
    @Test
    @Ignore
    public void testReadEncryptedPKCS8() throws Exception {
        String keyFile = "keys/pkcs8-aes256-encrypted.pem";
        try (
            InputStream keyStream = cl.getResourceAsStream(keyFile);
            Reader expectedReader = new InputStreamReader(cl.getResourceAsStream(keyFile));
        ) {
            PrivateKey actualKey = constructor.newInstance().read(keyStream, "password");

            PKCS8EncryptedPrivateKeyInfo expected =
                (PKCS8EncryptedPrivateKeyInfo) new PEMParser(expectedReader).readObject();

            // the PBE in JcePKCSPBEInputDecryptorProviderBuilder stands for "password based encryption"
            InputDecryptorProvider provider = new JcePKCSPBEInputDecryptorProviderBuilder()
                .setProvider(BC_PROVIDER)
                .build(PASSWORD);
            PrivateKeyInfo decryptedInfo = expected.decryptPrivateKeyInfo(provider);
            PrivateKey expectedKey = new JcaPEMKeyConverter()
                .setProvider(BC_PROVIDER)
                .getPrivateKey(decryptedInfo);
            assertEquals(actualKey, expectedKey);
        }
    }

    @Test
    public void testReadPKCS1() throws Exception {
        String keyFile = "keys/pkcs1-unencrypted.pem";
        try (
            InputStream keyStream = cl.getResourceAsStream(keyFile);
            Reader expectedReader = new InputStreamReader(cl.getResourceAsStream(keyFile));
        ) {
            PrivateKey actualKey = constructor.newInstance().read(keyStream, null);
            PEMKeyPair expected = (PEMKeyPair) new PEMParser(expectedReader).readObject();
            PrivateKey expectedKey = new JcaPEMKeyConverter()
                .setProvider(BC_PROVIDER)
                .getKeyPair(expected)
                .getPrivate();
            assertEquals(actualKey, expectedKey);
        }
    }

    @Test
    public void testReadEncryptedPKCS1() throws Exception {
        openPKCS1("keys/pkcs1-aes256-encrypted.pem", "password");
    }

    @Test
    public void testRead3DESEncryptedPKCS1() throws Exception {
        openPKCS1("keys/pkcs1-des-encrypted.pem", "password");
    }

    private void openPKCS1(String keyFile, String password) throws Exception {
        try (
            InputStream keyStream = cl.getResourceAsStream(keyFile);
            Reader expectedReader = new InputStreamReader(cl.getResourceAsStream(keyFile));
        ) {
            PrivateKey actualKey = constructor.newInstance().read(keyStream, password);
            PEMEncryptedKeyPair expected = (PEMEncryptedKeyPair) new PEMParser(expectedReader).readObject();

            PEMDecryptorProvider provider = new JcePEMDecryptorProviderBuilder()
                .setProvider(BC_PROVIDER)
                .build(PASSWORD);

            PEMKeyPair decryptedInfo = expected.decryptKeyPair(provider);
            PrivateKey expectedKey = new JcaPEMKeyConverter()
                .setProvider(BC_PROVIDER)
                .getKeyPair(decryptedInfo)
                .getPrivate();
            assertEquals(actualKey, expectedKey);
        }
    }
}
