/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.IdentityCertificateCurator;
import org.candlepin.model.Owner;
import org.candlepin.pki.DistinguishedName;
import org.candlepin.pki.KeyPairGenerator;
import org.candlepin.pki.PKIUtility;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ExpiryDateFunction;
import org.candlepin.util.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Set;

/**
 * DefaultIdentityCertServiceAdapterTest
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DefaultIdentityCertServiceAdapterTest {

    @Mock
    private PKIUtility pki;
    @Mock
    private IdentityCertificateCurator identityCertificateCurator;
    @Mock
    private CertificateSerialCurator serialCurator;
    @Mock
    private KeyPairGenerator keyPairGenerator;
    private DefaultIdentityCertServiceAdapter adapter;

    @BeforeEach
    public void setUp() {
        adapter = new DefaultIdentityCertServiceAdapter(pki, identityCertificateCurator,
            serialCurator, keyPairGenerator, new ExpiryDateFunction(1));
    }

    @Test
    public void testGenerate() throws GeneralSecurityException, IOException {
        Consumer consumer = mock(Consumer.class);
        when(consumer.getId()).thenReturn("42");
        when(consumer.getUuid()).thenReturn(Util.generateUUID());
        Owner owner = mock(Owner.class);
        when(owner.getKey()).thenReturn(TestUtil.randomString());
        when(consumer.getOwner()).thenReturn(owner);
        KeyPair kp = createKeyPair();
        when(keyPairGenerator.getKeyPair(consumer)).thenReturn(kp);
        when(identityCertificateCurator.get(consumer.getId())).thenReturn(null);
        when(serialCurator.create(any(CertificateSerial.class)))
            .thenAnswer((Answer<CertificateSerial>) invocation -> {
                Object[] args = invocation.getArguments();
                CertificateSerial cs = (CertificateSerial) args[0];
                cs.setId(42L);
                return cs;
            });
        when(pki.createX509Certificate(any(DistinguishedName.class), nullable(Set.class),
            any(Date.class), any(Date.class), any(KeyPair.class), any(BigInteger.class),
            nullable(String.class)))
            .thenReturn(mock(X509Certificate.class));
        when(pki.getPemEncoded(any(X509Certificate.class))).thenReturn(
            "x509cert".getBytes());
        when(pki.getPemEncoded(any(PrivateKey.class))).thenReturn(
            "priv".getBytes());
        when(identityCertificateCurator.create(any(IdentityCertificate.class)))
            .thenAnswer((Answer<IdentityCertificate>) invocation -> {
                Object[] args = invocation.getArguments();
                IdentityCertificate ic = (IdentityCertificate) args[0];
                ic.setId("42");
                return ic;
            });

        IdentityCertificate ic = adapter.generateIdentityCert(consumer);

        assertNotNull(ic);
        assertEquals("priv", ic.getKey());
        assertEquals("x509cert", ic.getCert());
        assertNotNull(ic.getCertAsBytes());
        assertNotNull(ic.getKeyAsBytes());
        verify(consumer).setIdCert(ic);
        verify(serialCurator).create(any(CertificateSerial.class));
    }

    @Test
    public void testReturnExisting() throws GeneralSecurityException, IOException {
        Consumer consumer = mock(Consumer.class);
        IdentityCertificate mockic = mock(IdentityCertificate.class);

        when(consumer.getIdCert()).thenReturn(mockic);
        when(identityCertificateCurator.get(mockic.getId())).thenReturn(mockic);
        when(identityCertificateCurator.get(consumer.getId())).thenReturn(mockic);

        IdentityCertificate ic = adapter.generateIdentityCert(consumer);

        assertNotNull(ic);
        assertEquals(ic, mockic);
    }

    @Test
    public void testRegenerateCallsDeletes() throws GeneralSecurityException, IOException {
        Consumer consumer = mock(Consumer.class);
        IdentityCertificate mockic = mock(IdentityCertificate.class);
        when(consumer.getIdCert()).thenReturn(mockic);
        when(consumer.getUuid()).thenReturn("test_uuid");
        Owner owner = mock(Owner.class);
        when(owner.getKey()).thenReturn(TestUtil.randomString());
        when(consumer.getOwner()).thenReturn(owner);
        when(mockic.getId()).thenReturn("43");
        when(identityCertificateCurator.get(mockic.getId())).thenReturn(mockic);


        KeyPair kp = createKeyPair();
        when(keyPairGenerator.getKeyPair(consumer)).thenReturn(kp);
        when(serialCurator.create(any(CertificateSerial.class)))
            .thenAnswer((Answer<CertificateSerial>) invocation -> {
                Object[] args = invocation.getArguments();
                CertificateSerial cs = (CertificateSerial) args[0];
                cs.setId(42L);
                return cs;
            });
        when(pki.createX509Certificate(any(DistinguishedName.class), nullable(Set.class),
            any(Date.class), any(Date.class), any(KeyPair.class), any(BigInteger.class),
            nullable(String.class)))
            .thenReturn(mock(X509Certificate.class));
        when(pki.getPemEncoded(any(X509Certificate.class))).thenReturn(
            "x509cert".getBytes());
        when(pki.getPemEncoded(any(PrivateKey.class))).thenReturn(
            "priv".getBytes());
        when(identityCertificateCurator.create(any(IdentityCertificate.class)))
            .thenAnswer((Answer<IdentityCertificate>) invocation -> {
                Object[] args = invocation.getArguments();
                IdentityCertificate ic = (IdentityCertificate) args[0];
                ic.setId("42");
                return ic;
            });

        IdentityCertificate ic = adapter.regenerateIdentityCert(consumer);

        verify(consumer).setIdCert(null);
        verify(identityCertificateCurator).delete(mockic);
        assertNotSame(ic, mockic);
        assertEquals("priv", ic.getKey());
        assertEquals("x509cert", ic.getCert());
        assertNotNull(ic.getCertAsBytes());
        assertNotNull(ic.getKeyAsBytes());
        verify(consumer).setIdCert(ic);
        verify(serialCurator).create(any(CertificateSerial.class));

    }

    @Test
    public void testRegenerate() throws GeneralSecurityException, IOException {
        Consumer consumer = mock(Consumer.class);
        when(consumer.getId()).thenReturn("42L");
        when(consumer.getUuid()).thenReturn(Util.generateUUID());
        Owner owner = mock(Owner.class);
        when(owner.getKey()).thenReturn(TestUtil.randomString());
        when(consumer.getOwner()).thenReturn(owner);

        when(identityCertificateCurator.get(consumer.getId())).thenReturn(null);


        KeyPair kp = createKeyPair();
        when(keyPairGenerator.getKeyPair(consumer)).thenReturn(kp);
        when(serialCurator.create(any(CertificateSerial.class)))
            .thenAnswer((Answer<CertificateSerial>) invocation -> {
                Object[] args = invocation.getArguments();
                CertificateSerial cs = (CertificateSerial) args[0];
                cs.setId(42L);
                return cs;
            });

        when(pki.createX509Certificate(any(DistinguishedName.class), nullable(Set.class),
            any(Date.class), any(Date.class), any(KeyPair.class), any(BigInteger.class),
            nullable(String.class)))
            .thenReturn(mock(X509Certificate.class));
        when(pki.getPemEncoded(any(X509Certificate.class))).thenReturn(
            "x509cert".getBytes());
        when(pki.getPemEncoded(any(PrivateKey.class))).thenReturn(
            "priv".getBytes());
        when(identityCertificateCurator.create(any(IdentityCertificate.class)))
            .thenAnswer((Answer<IdentityCertificate>) invocation -> {
                Object[] args = invocation.getArguments();
                IdentityCertificate ic = (IdentityCertificate) args[0];
                ic.setId("42");
                return ic;
            });

        IdentityCertificate ic = adapter.regenerateIdentityCert(consumer);

        assertNotNull(ic);
        verify(consumer, never()).setIdCert(null);
        verify(identityCertificateCurator, never()).delete(any(IdentityCertificate.class));
        assertEquals("priv", ic.getKey());
        assertEquals("x509cert", ic.getCert());
        assertNotNull(ic.getCertAsBytes());
        assertNotNull(ic.getKeyAsBytes());
        verify(consumer).setIdCert(ic);
        verify(serialCurator).create(any(CertificateSerial.class));

    }

    // can't mock a final class, so create a dummy one
    private KeyPair createKeyPair() {
        PublicKey pk = mock(PublicKey.class);
        PrivateKey ppk = mock(PrivateKey.class);
        return new KeyPair(pk, ppk);
    }
}
