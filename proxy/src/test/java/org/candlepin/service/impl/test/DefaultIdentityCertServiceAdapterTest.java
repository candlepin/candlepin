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
package org.candlepin.service.impl.test;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.IdentityCertificateCurator;
import org.candlepin.model.KeyPairCurator;
import org.candlepin.pki.PKIUtility;
import org.candlepin.service.impl.DefaultIdentityCertServiceAdapter;
import org.candlepin.util.ExpiryDateFunction;
import org.candlepin.util.Util;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;


/**
 * DefaultIdentityCertServiceAdapterTest
 */
@RunWith(MockitoJUnitRunner.class)
public class DefaultIdentityCertServiceAdapterTest {

    @Mock private PKIUtility pki;
    @Mock private IdentityCertificateCurator idcur;
    @Mock private KeyPairCurator kpc;
    @Mock private CertificateSerialCurator csc;
    private DefaultIdentityCertServiceAdapter dicsa;


    @Before
    public void setUp() {
        dicsa = new DefaultIdentityCertServiceAdapter(pki, idcur, kpc, csc,
            new ExpiryDateFunction(1));
    }

    // can't mock a final class, so create a dummy one
    private KeyPair createKeyPair() {
        PublicKey pk = mock(PublicKey.class);
        PrivateKey ppk = mock(PrivateKey.class);
        return new KeyPair(pk, ppk);
    }

    @Test
    public void testGenerate() throws GeneralSecurityException, IOException {
        Consumer consumer = mock(Consumer.class);
        when(consumer.getId()).thenReturn("42");
        when(consumer.getUuid()).thenReturn(Util.generateUUID());
        KeyPair kp = createKeyPair();
        when(kpc.getConsumerKeyPair(consumer)).thenReturn(kp);
        when(idcur.find(consumer.getId())).thenReturn(null);
        when(csc.create(any(CertificateSerial.class))).thenAnswer(
            new Answer<CertificateSerial>() {
                public CertificateSerial answer(InvocationOnMock invocation) {
                    Object[] args = invocation.getArguments();
                    CertificateSerial cs = (CertificateSerial) args[0];
                    cs.setId(42L);
                    return cs;
                }
            });

        when(pki.getPemEncoded(any(X509Certificate.class))).thenReturn(
            "x509cert".getBytes());
        when(pki.getPemEncoded(any(PrivateKey.class))).thenReturn(
            "priv".getBytes());
        when(idcur.create(any(IdentityCertificate.class))).thenAnswer(
            new Answer<IdentityCertificate>() {
                public IdentityCertificate answer(InvocationOnMock invocation) {
                    Object[] args = invocation.getArguments();
                    IdentityCertificate ic = (IdentityCertificate) args[0];
                    ic.setId("42");
                    return ic;
                }
            });

        IdentityCertificate ic = dicsa.generateIdentityCert(consumer);

        assertNotNull(ic);
        assertEquals("priv", ic.getKey());
        assertEquals("x509cert", ic.getCert());
        assertNotNull(ic.getCertAsBytes());
        assertNotNull(ic.getKeyAsBytes());
        verify(consumer).setIdCert(ic);
        verify(csc).create(any(CertificateSerial.class));
    }

    @Test
    public void testReturnExisting() throws GeneralSecurityException, IOException {
        Consumer consumer = mock(Consumer.class);
        IdentityCertificate mockic = mock(IdentityCertificate.class);

        when(consumer.getIdCert()).thenReturn(mockic);
        when(idcur.find(mockic.getId())).thenReturn(mockic);
        when(idcur.find(consumer.getId())).thenReturn(mockic);

        IdentityCertificate ic = dicsa.generateIdentityCert(consumer);

        assertNotNull(ic);
        assertEquals(ic, mockic);
    }

    @Test
    public void testRegenerateCallsDeletes() throws GeneralSecurityException, IOException {
        Consumer consumer = mock(Consumer.class);
        IdentityCertificate mockic = mock(IdentityCertificate.class);
        when(consumer.getIdCert()).thenReturn(mockic);
        when(mockic.getId()).thenReturn("43");
        when(idcur.find(mockic.getId())).thenReturn(mockic);


        KeyPair kp = createKeyPair();
        when(kpc.getConsumerKeyPair(consumer)).thenReturn(kp);
        when(csc.create(any(CertificateSerial.class))).thenAnswer(
            new Answer<CertificateSerial>() {
                public CertificateSerial answer(InvocationOnMock invocation) {
                    Object[] args = invocation.getArguments();
                    CertificateSerial cs = (CertificateSerial) args[0];
                    cs.setId(42L);
                    return cs;
                }
            });

        when(pki.getPemEncoded(any(X509Certificate.class))).thenReturn(
            "x509cert".getBytes());
        when(pki.getPemEncoded(any(PrivateKey.class))).thenReturn(
            "priv".getBytes());
        when(idcur.create(any(IdentityCertificate.class))).thenAnswer(
            new Answer<IdentityCertificate>() {
                public IdentityCertificate answer(InvocationOnMock invocation) {
                    Object[] args = invocation.getArguments();
                    IdentityCertificate ic = (IdentityCertificate) args[0];
                    ic.setId("42");
                    return ic;
                }
            });

        IdentityCertificate ic = dicsa.regenerateIdentityCert(consumer);

        verify(consumer).setIdCert(null);
        verify(idcur).delete(mockic);
        assertNotSame(ic, mockic);
        assertEquals("priv", ic.getKey());
        assertEquals("x509cert", ic.getCert());
        assertNotNull(ic.getCertAsBytes());
        assertNotNull(ic.getKeyAsBytes());
        verify(consumer).setIdCert(ic);
        verify(csc).create(any(CertificateSerial.class));

    }

    @Test
    public void testRegenerate() throws GeneralSecurityException, IOException {
        Consumer consumer = mock(Consumer.class);
        when(consumer.getId()).thenReturn("42L");
        when(consumer.getUuid()).thenReturn(Util.generateUUID());
        when(idcur.find(consumer.getId())).thenReturn(null);


        KeyPair kp = createKeyPair();
        when(kpc.getConsumerKeyPair(consumer)).thenReturn(kp);
        when(csc.create(any(CertificateSerial.class))).thenAnswer(
            new Answer<CertificateSerial>() {
                public CertificateSerial answer(InvocationOnMock invocation) {
                    Object[] args = invocation.getArguments();
                    CertificateSerial cs = (CertificateSerial) args[0];
                    cs.setId(42L);
                    return cs;
                }
            });

        when(pki.getPemEncoded(any(X509Certificate.class))).thenReturn(
            "x509cert".getBytes());
        when(pki.getPemEncoded(any(PrivateKey.class))).thenReturn(
            "priv".getBytes());
        when(idcur.create(any(IdentityCertificate.class))).thenAnswer(
            new Answer<IdentityCertificate>() {
                public IdentityCertificate answer(InvocationOnMock invocation) {
                    Object[] args = invocation.getArguments();
                    IdentityCertificate ic = (IdentityCertificate) args[0];
                    ic.setId("42");
                    return ic;
                }
            });

        IdentityCertificate ic = dicsa.regenerateIdentityCert(consumer);

        assertNotNull(ic);
        verify(consumer, never()).setIdCert(null);
        verify(idcur, never()).delete(any(IdentityCertificate.class));
        assertEquals("priv", ic.getKey());
        assertEquals("x509cert", ic.getCert());
        assertNotNull(ic.getCertAsBytes());
        assertNotNull(ic.getKeyAsBytes());
        verify(consumer).setIdCert(ic);
        verify(csc).create(any(CertificateSerial.class));

    }
}
