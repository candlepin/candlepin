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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.model.Consumer;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.IdentityCertificateCurator;
import org.candlepin.model.Owner;
import org.candlepin.pki.certs.IdentityCertificateGenerator;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.GeneralSecurityException;

@ExtendWith(MockitoExtension.class)
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
    public void testGenerate() throws GeneralSecurityException {
        Owner owner = createOwner();
        Consumer consumer = new Consumer()
            .setId("42")
            .setUuid(Util.generateUUID())
            .setOwner(owner);
        IdentityCertificate certificate = new IdentityCertificate();
        when(this.identityCertificateGenerator.generate(any(Consumer.class))).thenReturn(certificate);

        IdentityCertificate generatedCert = this.adapter.generateIdentityCert(consumer);

        assertNotNull(consumer.getIdCert());
        assertNotNull(generatedCert);
    }

    @Test
    public void testReturnExisting() throws GeneralSecurityException {
        Owner owner = createOwner();
        IdentityCertificate certificate = createCert("42");
        Consumer consumer = new Consumer()
            .setId("42")
            .setUuid(Util.generateUUID())
            .setOwner(owner)
            .setIdCert(certificate);
        when(this.identityCertificateCurator.get(certificate.getId())).thenReturn(certificate);

        IdentityCertificate identityCertificate = this.adapter.generateIdentityCert(consumer);

        assertNotNull(identityCertificate);
        assertEquals(identityCertificate, certificate);
        verifyNoInteractions(this.identityCertificateGenerator);
    }

    @Test
    public void testRegenerate() throws GeneralSecurityException {
        Owner owner = createOwner();
        IdentityCertificate oldCert = createCert("42");
        IdentityCertificate newCert = createCert("45");
        Consumer consumer = new Consumer()
            .setId("42")
            .setUuid(Util.generateUUID())
            .setOwner(owner)
            .setIdCert(oldCert);
        when(this.identityCertificateCurator.get(oldCert.getId())).thenReturn(oldCert);
        when(this.identityCertificateGenerator.generate(any(Consumer.class))).thenReturn(newCert);

        IdentityCertificate identityCertificate = this.adapter.regenerateIdentityCert(consumer);

        assertEquals(newCert.getId(), consumer.getIdCert().getId());
        verify(this.identityCertificateCurator).delete(oldCert);
        assertNotSame(identityCertificate, oldCert);
    }

    @Test
    public void testDeleteNothingToDo() {
        Owner owner = createOwner();
        Consumer consumer = new Consumer()
            .setId("42")
            .setUuid(Util.generateUUID())
            .setOwner(owner);

        this.adapter.deleteIdentityCert(consumer);

        verifyNoMoreInteractions(this.identityCertificateCurator);
    }

    @Test
    public void testDelete() throws GeneralSecurityException {
        Owner owner = createOwner();
        IdentityCertificate certificate = createCert("42");
        Consumer consumer = new Consumer()
            .setId("42")
            .setUuid(Util.generateUUID())
            .setOwner(owner)
            .setIdCert(certificate);
        when(this.identityCertificateCurator.get(certificate.getId())).thenReturn(certificate);

        this.adapter.deleteIdentityCert(consumer);

        verify(this.identityCertificateCurator).delete(certificate);
    }

    private IdentityCertificate createCert(String certId) {
        IdentityCertificate certificate = new IdentityCertificate();
        certificate.setId(certId);
        return certificate;
    }

    private Owner createOwner() {
        return new Owner()
            .setId("test_owner")
            .setKey(TestUtil.randomString());
    }
}
