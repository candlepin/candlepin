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
package org.candlepin.controller;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bouncycastle.asn1.x509.CRLNumber;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.x509.X509V2CRLGenerator;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.pki.PKIReader;
import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.X509CRLEntryWrapper;
import org.candlepin.pki.impl.BouncyCastlePKIUtility;
import org.candlepin.pki.impl.DefaultSubjectKeyIdentifierWriter;
import org.candlepin.util.Util;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.security.auth.x500.X500Principal;

/**
 * CRLGeneratorTest
 */
@SuppressWarnings("deprecation")
@RunWith(MockitoJUnitRunner.class)
public class CrlGeneratorTest {

    private static final KeyPair KP = generateKP();
    private static final X509Certificate CERT = generateCertificate();

    @Mock private PKIReader pkiReader;
    @Mock private CertificateSerialCurator curator;
    private PKIUtility pkiUtility;

    private CrlGenerator generator;

    public static KeyPair generateKP() {
        KeyPairGenerator kpg;
        try {
            kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            return kpg.generateKeyPair();
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static X509Certificate generateCertificate() {
        X500Principal principal = new X500Principal(generateFakePrincipal());
        X509V3CertificateGenerator gen = new X509V3CertificateGenerator();
        gen.setSerialNumber(BigInteger.TEN);
        gen.setNotBefore(Util.yesterday());
        gen.setNotAfter(Util.getFutureDate(2));
        gen.setSubjectDN(principal);
        gen.setIssuerDN(principal);
        gen.setPublicKey(KP.getPublic());
        gen.setSignatureAlgorithm("SHA1WITHRSA");
        try {
            return gen.generate(KP.getPrivate());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String generateFakePrincipal() {
        return "CN=test, UID=" + UUID.randomUUID();
    }

    @Before
    public void init() throws Exception {
        this.pkiUtility = new BouncyCastlePKIUtility(pkiReader,
            new DefaultSubjectKeyIdentifierWriter());
        this.generator = new CrlGenerator(curator, pkiUtility);

        when(pkiReader.getCaKey()).thenReturn(KP.getPrivate());
        when(pkiReader.getCACert()).thenReturn(CERT);
    }

    @Test
    public void crlNumberWithNull() {
        assertEquals(BigInteger.ZERO, generator.getCRLNumber(null));
    }

    @Test
    public void crlNumberWithCert() throws Exception {
        X509V2CRLGenerator g = new X509V2CRLGenerator();
        g.setIssuerDN(new X500Principal("CN=test, UID=" + UUID.randomUUID()));
        g.setThisUpdate(new Date());
        g.setNextUpdate(Util.tomorrow());
        g.setSignatureAlgorithm("SHA1withRSA");
        g.addExtension(X509Extensions.CRLNumber, false,
            new CRLNumber(BigInteger.TEN));

        X509CRL x509crl = g.generate(KP.getPrivate());
        assertEquals(BigInteger.TEN, this.generator.getCRLNumber(x509crl));
    }

    @Test
    public void serialsTransfered() {
        List<CertificateSerial> serials = getStubCSList();

        when(this.curator.retrieveTobeCollectedSerials())
            .thenReturn(serials);
        List<X509CRLEntryWrapper> entries = this.generator
            .getNewSerialsToAppendAndSetThemConsumed();
        assertEquals(entries.size(), serials.size());
        verify(this.curator).saveOrUpdateAll(serials);
        for (int i = 0; i < serials.size(); i++) {
            CertificateSerial cs = serials.get(i);
            assertEquals(cs.getSerial(), entries.get(i).getSerialNumber());
        }
    }

    @Test
    public void serialsEmptyList() {
        when(this.curator.retrieveTobeCollectedSerials())
            .thenReturn(new ArrayList<CertificateSerial>());
        List<X509CRLEntryWrapper> entries = this.generator
            .getNewSerialsToAppendAndSetThemConsumed();
        assertEquals(0, entries.size());
    }

    @Test
    @SuppressWarnings("serial")
    public void emptyExpiredSerials() {
        Set<? extends X509CRLEntry> set = stubX509CRLEntries();

        when(this.curator.getExpiredSerials())
            .thenReturn(new ArrayList<CertificateSerial>() {
                {
                    add(stubCS(1L, new Date()));
                    add(stubCS(10L, new Date()));
                }
            });
        Set<? extends X509CRLEntry> result = this.generator.removeExpiredSerials(set);
        assertEquals(1, result.size());

        X509CRLEntry entry = result.iterator().next();
        assertEquals(BigInteger.ZERO, entry.getSerialNumber());
    }

    @Test
    @SuppressWarnings("serial")
    public void expiredSerials() {
        Set<? extends X509CRLEntry> set = stubX509CRLEntries();

        when(this.curator.getExpiredSerials())
            .thenReturn(new ArrayList<CertificateSerial>() {
                {
                    add(stubCS(12L, new Date()));
                    add(stubCS(13L, new Date()));
                }
            });
        Set<? extends X509CRLEntry> result = this.generator.removeExpiredSerials(set);
        assertEquals(set.size(), result.size());
    }

    @Test
    public void emptyRevocationsReturnsUntouched() throws Exception {
        // there's gotta be a way to reduce to a set of mocks

        KeyPair kp = CrlGeneratorTest.generateKP();
        X509V2CRLGenerator g = new X509V2CRLGenerator();
        g.setIssuerDN(new X500Principal("CN=test, UID=" + UUID.randomUUID()));
        g.setThisUpdate(new Date());
        g.setNextUpdate(Util.tomorrow());
        g.setSignatureAlgorithm("SHA1withRSA");
        g.addExtension(X509Extensions.CRLNumber, false,
            new CRLNumber(BigInteger.TEN));
        X509CRL x509crl = g.generate(kp.getPrivate());

        // now we need to remove one of those serials
        List<CertificateSerial> toremove = new ArrayList<CertificateSerial>() {
            {
                add(stubCS(100L, new Date()));
            }
        };

        X509CRL untouchedcrl = generator.removeEntries(x509crl, toremove);
        assertEquals(x509crl, untouchedcrl);
    }

    @Test
    @SuppressWarnings("serial")
    public void removeEntries() throws Exception {
        // there's gotta be a way to reduce to a set of mocks

        KeyPair kp = CrlGeneratorTest.generateKP();
        X509V2CRLGenerator g = new X509V2CRLGenerator();
        g.setIssuerDN(new X500Principal("CN=test, UID=" + UUID.randomUUID()));
        g.setThisUpdate(new Date());
        g.setNextUpdate(Util.tomorrow());
        g.setSignatureAlgorithm("SHA1withRSA");
        g.addExtension(X509Extensions.CRLNumber, false,
            new CRLNumber(BigInteger.TEN));
        X509CRL x509crl = g.generate(kp.getPrivate());

        List<CertificateSerial> serials = getStubCSList();
        List<X509CRLEntryWrapper> entries = Util.newList();
        for (CertificateSerial serial : serials) {
            entries.add(new X509CRLEntryWrapper(serial.getSerial(),
                new Date()));
            serial.setCollected(true);
        }

        x509crl = pkiUtility.createX509CRL(entries, BigInteger.TEN);
        assertEquals(3, x509crl.getRevokedCertificates().size());


        // now we need to remove one of those serials
        List<CertificateSerial> toremove = new ArrayList<CertificateSerial>() {
            {
                add(stubCS(100L, new Date()));
            }
        };

        X509CRL updatedcrl = generator.removeEntries(x509crl, toremove);
        Set<? extends X509CRLEntry> revoked = updatedcrl.getRevokedCertificates();
        assertEquals(2, revoked.size());
    }

    @Test
    public void updateCRLWithNullInput() {
        List<CertificateSerial> serials = getStubCSList();
        when(this.curator.retrieveTobeCollectedSerials())
            .thenReturn(serials);
        X509CRL x509crl = this.generator.syncCRLWithDB((X509CRL) null);
        verify(this.curator).deleteExpiredSerials();
        assertEquals(BigInteger.ONE, this.generator.getCRLNumber(x509crl));
        Set<? extends X509CRLEntry> entries = x509crl.getRevokedCertificates();
        Set<BigInteger> nos = Util.newSet();
        for (X509CRLEntry entry : entries) {
            nos.add(entry.getSerialNumber());
        }
        assertTrue(nos.contains(BigInteger.ONE));
        assertTrue(nos.contains(new BigInteger("100")));
        assertTrue(nos.contains(new BigInteger("1235465")));
    }

    @Test
    @SuppressWarnings({ "unchecked", "serial", "rawtypes" })
    public void testUpdateCRLWithMockedCRL() {
        X509CRL oldCert = mock(X509CRL.class);
        Set<? extends X509CRLEntry> crls = stubX509CRLEntries(); //0, 1, 10,

        // byte array captured from previous runs - represents 1
        when(oldCert.getExtensionValue("2.5.29.20"))
            .thenReturn(new byte[] {4, 3, 2, 1, 1});
        when(oldCert.getRevokedCertificates())
            .thenReturn((Set) crls);
        when(this.curator.getExpiredSerials())
            .thenReturn(getStubCSList());  //1, 100, 1235465
        when(this.curator.retrieveTobeCollectedSerials()) //1001, 1002
            .thenReturn(new ArrayList<CertificateSerial>() {
                {
                    add(stubCS(1001L, new Date()));
                    add(stubCS(1002L, new Date()));
                }
            });
        X509CRL newCRL = this.generator.syncCRLWithDB(oldCert);

        verify(this.curator, times(1)).retrieveTobeCollectedSerials();
        verify(this.curator, times(1)).deleteExpiredSerials();
        verify(this.curator, times(1)).getExpiredSerials();

        assertEquals(new BigInteger("2"), this.generator.getCRLNumber(newCRL));

        Set<? extends X509CRLEntry> entries = newCRL.getRevokedCertificates();
        Set<BigInteger> nos = Util.newSet();
        for (X509CRLEntry entry : entries) {
            nos.add(entry.getSerialNumber());
        }
        long [] expectedSerials = new long[] { 1001, 1002, 0, 10};
        assertEquals(nos.size(), expectedSerials.length);
        for (int i = 0; i < expectedSerials.length; i++) {
            nos.contains(Util.toBigInt(expectedSerials[i]));
        }
    }

    @Test
    public void decodeValue() throws Exception {
        // there's gotta be a way to reduce to a set of mocks
        KeyPair kp = CrlGeneratorTest.generateKP();
        X509V2CRLGenerator g = new X509V2CRLGenerator();
        g.setIssuerDN(new X500Principal("CN=test, UID=" + UUID.randomUUID()));
        g.setThisUpdate(new Date());
        g.setNextUpdate(Util.tomorrow());
        g.setSignatureAlgorithm("SHA1withRSA");
        g.addExtension(X509Extensions.CRLNumber, false,
            new CRLNumber(BigInteger.TEN));

        X509CRL x509crl = g.generate(kp.getPrivate());

        assertEquals("10", pkiUtility.decodeDERValue(x509crl.getExtensionValue(
            X509Extensions.CRLNumber.getId())));
    }

    @SuppressWarnings("serial")
    private List<CertificateSerial> getStubCSList() {
        return new ArrayList<CertificateSerial>() {
            {
                add(stubCS(1L, new Date()));
                add(stubCS(100L, new Date()));
                add(stubCS(1235465L, new Date()));
            }
        };
    }

    private CertificateSerial stubCS(Long id, Date expiration) {
        CertificateSerial cs = new CertificateSerial(id, expiration);
        cs.setCollected(false);
        return cs;
    }

    private Set<? extends X509CRLEntry> stubX509CRLEntries() {
        Set<X509CRLEntry> set = new LinkedHashSet<X509CRLEntry>();
        set.add(mockCRL(BigInteger.ONE, new Date()));
        set.add(mockCRL(BigInteger.TEN, new Date()));
        set.add(mockCRL(BigInteger.ZERO, new Date()));
        return set;
    }

    private X509CRLEntry mockCRL(BigInteger serial, Date dt) {
        X509CRLEntry entry = mock(X509CRLEntry.class);
        when(entry.getSerialNumber()).thenReturn(serial);
        when(entry.getRevocationDate()).thenReturn(dt);
        return entry;
    }
}
