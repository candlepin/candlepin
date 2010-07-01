/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.pinsetter.tasks;

import static junit.framework.Assert.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.x509.CRLNumber;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.x509.X509V2CRLGenerator;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.model.CertificateSerial;
import org.fedoraproject.candlepin.model.CertificateSerialCurator;
import org.fedoraproject.candlepin.pinsetter.tasks.CertificateRevocationListTask.SimpleCRLEntry;
import org.fedoraproject.candlepin.pki.PKIReader;
import org.fedoraproject.candlepin.util.Util;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.quartz.JobExecutionException;

/**
 * CertificateRevocationListTaskTest
 */
@RunWith(MockitoJUnitRunner.class)
public class CertificateRevocationListTaskTest{
    private CertificateRevocationListTask task;
     
    @Mock private PKIReader pkiReader;
     
    @Mock private Config config;
     
    @Mock private CertificateSerialCurator curator;

    private static final KeyPair KP = generateKP();
    
    private static final X509Certificate CERT = generateCertificate();
    
    @Before
    public void init() {
        this.task = new CertificateRevocationListTask(pkiReader, config,
            curator, "SHA1withRSA");
        try {
            when(pkiReader.getCaKey()).thenReturn(KP.getPrivate());
            when(pkiReader.getCACert()).thenReturn(CERT);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @return
     */
    private static KeyPair generateKP() {
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

    @Test
    public void testUpdateURLStreams() throws JobExecutionException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String principal = "CN=test, UID=" + UUID.randomUUID();
        this.task.updateCRL(null, principal, out);
    }
    
    @Test
    public void testGetCRLNumberWithNull() {
        assertEquals(task.getCRLNumber(null), BigInteger.ZERO);
    }
    
    @Test
    public void testGetCRLNumberWithCert() {
        X509V2CRLGenerator generator = new X509V2CRLGenerator();
        generator.setIssuerDN(new X500Principal("CN=test, UID=" + UUID.randomUUID()));
        generator.setThisUpdate(new Date());
        generator.setNextUpdate(Util.tomorrow());
        generator.setSignatureAlgorithm("SHA1withRSA");
        generator.addExtension(X509Extensions.CRLNumber, false,
            new CRLNumber(BigInteger.TEN));
        try {
            X509CRL x509crl = generator.generate(KP.getPrivate());
            assertEquals(task.getCRLNumber(x509crl), BigInteger.TEN);
        }
       
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    
    @Test
    public void testToSimpleCRLEntries() {
        LinkedHashSet<X509CRLEntry> set = new LinkedHashSet<X509CRLEntry>();
        set.add(mockCRL(BigInteger.ONE, new Date()));
        set.add(mockCRL(BigInteger.TEN, new Date()));
        java.util.List<SimpleCRLEntry> result = this.task.toSimpleCRLEntries(set);
        assertEquals(result.size(), 2);
        int i = 0;
        for (Iterator<X509CRLEntry> iterator = set.iterator(); iterator.hasNext();) {
            X509CRLEntry crlEntry = (X509CRLEntry) iterator.next();
            assertEquals(crlEntry.getSerialNumber(), result.get(i).serialNumber);
            assertEquals(crlEntry.getRevocationDate(), result.get(i).revocationDate);
            ++i;
        }
    }
    

    @Test
    public void testGetNewSerialsToAppendAndSetThemConsumed1() {
        List<CertificateSerial> serials = getStubCSList();
        
        when(this.curator.retrieveTobeCollectedSerials())
            .thenReturn(serials);
        List<SimpleCRLEntry> entries = this.task.getNewSerialsToAppendAndSetThemConsumed();
        assertEquals(entries.size(), serials.size());
        Mockito.verify(this.curator).saveOrUpdateAll(serials);
        for (int i = 0; i < serials.size(); i++) {
            CertificateSerial cs = serials.get(i);
            assertEquals(cs.getSerial(), entries.get(i).serialNumber); 
            assertEquals(cs.getExpiration(), entries.get(i).revocationDate);
        }
    }
    
    @Test
    public void testGetNewSerialsToAppendAndSetThemConsumed2() {
        when(this.curator.retrieveTobeCollectedSerials())
            .thenReturn(new ArrayList<CertificateSerial>());
        List<SimpleCRLEntry> entries = this.task.getNewSerialsToAppendAndSetThemConsumed();
        assertEquals(entries.size(), 0);
    }
    
    @Test @SuppressWarnings("serial")
    public void testRemoveExpiredSerials1() {
        Set<? extends X509CRLEntry> set = stubX509CRLEntries();
        
        when(this.curator.getExpiredSerials())
            .thenReturn(new ArrayList<CertificateSerial>() {
                {
                    add(stubCS(1L, new Date()));
                    add(stubCS(10L, new Date()));
                }
            });
        Set<? extends X509CRLEntry> result = this.task.removeExpiredSerials(set);
        assertEquals(result.size(), 1);
        assertEquals(result.iterator().next().getSerialNumber(), BigInteger.ZERO);
    }
    
    @Test  @SuppressWarnings("serial")
    public void testRemoveExpiredSerials2() {
        Set<? extends X509CRLEntry> set = stubX509CRLEntries();
        
        when(this.curator.getExpiredSerials())
            .thenReturn(new ArrayList<CertificateSerial>() {
                {
                    add(stubCS(12L, new Date()));
                    add(stubCS(13L, new Date()));
                }
            });
        Set<? extends X509CRLEntry> result = this.task.removeExpiredSerials(set);
        assertEquals(result.size(), set.size());
    }

    /**
     * @return
     */
    private Set<? extends X509CRLEntry> stubX509CRLEntries() {
        Set<X509CRLEntry> set = new LinkedHashSet<X509CRLEntry>();
        set.add(mockCRL(BigInteger.ONE, new Date()));
        set.add(mockCRL(BigInteger.TEN, new Date()));
        set.add(mockCRL(BigInteger.ZERO, new Date()));
        return set;
    }
    
    @Test
    public void testUpdateCRLWithNullInput() {
        List<CertificateSerial> serials = getStubCSList();
        when(this.curator.retrieveTobeCollectedSerials())
            .thenReturn(serials);
        X509CRL x509crl = this.task.updateCRL((X509CRL) null, generateFakePrincipal());
        verify(this.curator).deleteExpiredSerials();
        assertEquals(BigInteger.ONE, this.task.getCRLNumber(x509crl));
        Set<? extends X509CRLEntry> entries = x509crl.getRevokedCertificates();
        Set<BigInteger> nos = Util.newSet();
        for (X509CRLEntry entry : entries) {
            nos.add(entry.getSerialNumber());
        }
        assertTrue(nos.contains(BigInteger.ONE));
        assertTrue(nos.contains(new BigInteger("100")));
        assertTrue(nos.contains(new BigInteger("1235465")));
    }
    
    @SuppressWarnings({ "unchecked", "serial" })
    @Test
    public void testUpdateCRLWithMockedCRL() {
        X509CRL oldCert = mock(X509CRL.class);
        Set crls = (Set) stubX509CRLEntries(); //0, 1, 10,
      //byte array captured from previous runs - represents 1 
        when(oldCert.getExtensionValue("2.5.29.20"))
            .thenReturn(new byte[] {4, 3, 2, 1, 1}); 
        when(oldCert.getRevokedCertificates())
            .thenReturn(crls);
        when(this.curator.getExpiredSerials())
            .thenReturn(getStubCSList());  //1, 100, 1235465
        when(this.curator.retrieveTobeCollectedSerials()) //1001, 1002
            .thenReturn(new ArrayList<CertificateSerial>() {
                {
                    add(stubCS(1001L, new Date()));
                    add(stubCS(1002L, new Date()));
                }
            });
        X509CRL newCRL = this.task.updateCRL(oldCert, generateFakePrincipal());
        
        verify(this.curator, times(1)).retrieveTobeCollectedSerials();
        verify(this.curator, times(1)).deleteExpiredSerials();
        verify(this.curator, times(1)).getExpiredSerials();
        
        assertEquals(new BigInteger("2"), this.task.getCRLNumber(newCRL));
        
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
    
    private X509CRLEntry mockCRL(BigInteger serial, Date dt) {
        X509CRLEntry entry = mock(X509CRLEntry.class);
        when(entry.getSerialNumber()).thenReturn(serial);
        when(entry.getRevocationDate()).thenReturn(dt);
        return entry;
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
    
    private CertificateSerial stubCS(long id, Date expiration) {
        CertificateSerial cs = new CertificateSerial(id, expiration);
        cs.setCollected(false);
        return cs;
    }
    
    public static String generateFakePrincipal() {
        return "CN=test, UID=" + UUID.randomUUID(); 
    }
    
    public static X509Certificate generateCertificate() {
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
}
