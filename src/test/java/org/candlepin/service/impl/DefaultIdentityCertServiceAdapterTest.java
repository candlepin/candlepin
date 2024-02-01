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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.model.Consumer;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.IdentityCertificateCurator;
import org.candlepin.pki.certs.IdentityCertificateGenerator;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * DefaultIdentityCertServiceAdapterTest
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DefaultIdentityCertServiceAdapterTest {

    @Mock
    private IdentityCertificateCurator identityCertificateCurator;
    @Mock
    private IdentityCertificateGenerator identityCertificateGenerator;
    private DefaultIdentityCertServiceAdapter adapter;


    @BeforeEach
    public void setUp() {
        this.adapter = new DefaultIdentityCertServiceAdapter(
            this.identityCertificateCurator, this.identityCertificateGenerator);
    }

    @Test
    public void shouldDeleteCertificate() {
        IdentityCertificate certificate = createIdentityCertificate();
        Consumer consumer = new Consumer().setIdCert(certificate);
        when(this.identityCertificateCurator.get(anyString())).thenReturn(certificate);

        this.adapter.deleteIdentityCert(consumer);

        verify(this.identityCertificateCurator).delete(certificate);
    }

    @Test
    public void shouldReturnCachedCert() {
        IdentityCertificate certificate = createIdentityCertificate();
        Consumer consumer = new Consumer().setIdCert(certificate);
        when(this.identityCertificateCurator.get(anyString())).thenReturn(certificate);

        IdentityCertificate result = this.adapter.generateIdentityCert(consumer);

        assertEquals(certificate, result);
        verifyNoInteractions(this.identityCertificateGenerator);
    }

    @Test
    public void shouldGenerateMissingCertificate() {
        IdentityCertificate certificate = createIdentityCertificate();
        Consumer consumer = new Consumer().setIdCert(certificate);
        when(this.identityCertificateGenerator.generate(any(Consumer.class))).thenReturn(certificate);

        IdentityCertificate result = this.adapter.generateIdentityCert(consumer);

        assertEquals(certificate, result);
    }

    @Test
    public void regenerateShouldDeleteOldCertificate() {
        IdentityCertificate certificate = createIdentityCertificate();
        Consumer consumer = new Consumer().setIdCert(certificate);
        when(this.identityCertificateCurator.get(anyString())).thenReturn(certificate);
        when(this.identityCertificateGenerator.generate(any(Consumer.class)))
            .thenReturn(createIdentityCertificate());

        IdentityCertificate result = this.adapter.regenerateIdentityCert(consumer);

        assertNotEquals(certificate, result);
        verify(this.identityCertificateCurator).delete(certificate);
    }

    private static IdentityCertificate createIdentityCertificate() {
        IdentityCertificate certificate = new IdentityCertificate();
        certificate.setId(TestUtil.randomString("id_cert"));
        return certificate;
    }

}
