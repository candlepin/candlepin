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

package org.candlepin.pki.certs;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.config.TestConfig;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.IdentityCertificateCurator;
import org.candlepin.model.KeyPairDataCurator;
import org.candlepin.model.Owner;
import org.candlepin.pki.KeyPairGenerator;
import org.candlepin.pki.PemEncoder;
import org.candlepin.pki.impl.BouncyCastleKeyPairGenerator;
import org.candlepin.pki.impl.BouncyCastlePemEncoder;
import org.candlepin.pki.impl.BouncyCastleSecurityProvider;
import org.candlepin.pki.impl.BouncyCastleSubjectKeyIdentifierWriter;
import org.candlepin.test.CertificateReaderForTesting;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.cert.CertificateException;

class IdentityCertificateGeneratorTest {
    private IdentityCertificateCurator identityCertificateCurator;
    private CertificateSerialCurator serialCurator;
    private X509CertificateBuilder certificateBuilder;
    private IdentityCertificateGenerator identityCertificateGenerator;

    @BeforeEach
    public void setUp() throws CertificateException, IOException {
        BouncyCastleSecurityProvider securityProvider = new BouncyCastleSecurityProvider();
        KeyPairGenerator keyPairGenerator = new BouncyCastleKeyPairGenerator(
            securityProvider, mock(KeyPairDataCurator.class));
        PemEncoder pemEncoder = new BouncyCastlePemEncoder();
        this.identityCertificateCurator = mock(IdentityCertificateCurator.class);
        this.serialCurator = mock(CertificateSerialCurator.class);
        this.certificateBuilder = new X509CertificateBuilder(
            new CertificateReaderForTesting(), securityProvider,
            new BouncyCastleSubjectKeyIdentifierWriter());
        this.identityCertificateGenerator = new IdentityCertificateGenerator(
            TestConfig.defaults(),
            pemEncoder,
            keyPairGenerator,
            this.identityCertificateCurator,
            this.serialCurator,
            () -> this.certificateBuilder
        );
    }

    @Test
    public void shouldGenerateNewCertificate() {
        Owner owner = new Owner().setId("test_id").setKey("test_owner");
        Consumer consumer = new Consumer()
            .setUuid("test_uuid")
            .setOwner(owner)
            .setName("name");
        when(this.serialCurator.create(any(CertificateSerial.class))).thenAnswer(invocation -> {
            CertificateSerial argument = invocation.getArgument(0);
            argument.setSerial(123L);
            return argument;
        });
        when(this.identityCertificateCurator.create(any(IdentityCertificate.class)))
            .thenAnswer(returnsFirstArg());

        IdentityCertificate certificate = this.identityCertificateGenerator.generate(consumer);

        assertNotNull(certificate);
    }

    @Test
    public void shouldReturnCachedCertificate() {
        Owner owner = new Owner().setId("test_id").setKey("test_owner");
        Consumer consumer = new Consumer()
            .setUuid("test_uuid")
            .setOwner(owner)
            .setName("name")
            .setIdCert(new IdentityCertificate());
        when(this.serialCurator.create(any(CertificateSerial.class))).thenAnswer(invocation -> {
            CertificateSerial argument = invocation.getArgument(0);
            argument.setSerial(123L);
            return argument;
        });
        when(this.identityCertificateCurator.get(any())).thenReturn(new IdentityCertificate());

        IdentityCertificate certificate = this.identityCertificateGenerator.generate(consumer);

        assertNotNull(certificate);
    }

    @Test
    public void shouldReGenerateNewCertificate() {
        Owner owner = new Owner().setId("test_id").setKey("test_owner");
        Consumer consumer = new Consumer()
            .setUuid("test_uuid")
            .setOwner(owner)
            .setName("name");
        when(this.serialCurator.create(any(CertificateSerial.class))).thenAnswer(invocation -> {
            CertificateSerial argument = invocation.getArgument(0);
            argument.setSerial(123L);
            return argument;
        });
        when(this.identityCertificateCurator.create(any(IdentityCertificate.class)))
            .thenAnswer(returnsFirstArg());

        IdentityCertificate certificate = this.identityCertificateGenerator.regenerate(consumer);

        assertNotNull(certificate);
    }

    @Test
    public void shouldReGenerateCachedCertificate() {
        Owner owner = new Owner().setId("test_id").setKey("test_owner");
        IdentityCertificate existingCert = new IdentityCertificate();
        existingCert.setId(TestUtil.randomString());
        Consumer consumer = new Consumer()
            .setUuid("test_uuid")
            .setOwner(owner)
            .setName("name")
            .setIdCert(existingCert);
        when(this.serialCurator.create(any(CertificateSerial.class))).thenAnswer(invocation -> {
            CertificateSerial argument = invocation.getArgument(0);
            argument.setSerial(123L);
            return argument;
        });
        when(this.identityCertificateCurator.get(any())).thenReturn(existingCert);
        when(this.identityCertificateCurator.create(any(IdentityCertificate.class)))
            .thenAnswer(returnsFirstArg());

        IdentityCertificate certificate = this.identityCertificateGenerator.regenerate(consumer);

        assertNotNull(certificate);
        assertNotEquals(existingCert.getId(), certificate.getId());
    }
}
