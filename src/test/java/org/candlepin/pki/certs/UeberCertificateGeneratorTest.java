/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.config.Configuration;
import org.candlepin.config.TestConfig;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Owner;
import org.candlepin.model.UeberCertificate;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.OidUtil;
import org.candlepin.pki.Scheme;
import org.candlepin.service.impl.DefaultUniqueIdGenerator;
import org.candlepin.test.CryptoUtil;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.util.X509ExtensionUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.stream.Stream;



public class UeberCertificateGeneratorTest extends DatabaseTestFixture {

    private static Stream<Arguments> schemeSource() {
        return CryptoUtil.SUPPORTED_SCHEMES.values()
            .stream()
            .map(Arguments::of);
    }

    private UeberCertificateGenerator buildGenerator() {
        Configuration config = TestConfig.defaults();
        CryptoManager cryptoManager = CryptoUtil.getCryptoManager(config);

        return new UeberCertificateGenerator(
            new DefaultUniqueIdGenerator(),
            this.certSerialCurator,
            this.ueberCertificateCurator,
            this.consumerTypeCurator,
            cryptoManager,
            new X509ExtensionUtil(config),
            CryptoUtil.getPemEncoder());
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGenerateCertificate(Scheme scheme) throws Exception {
        Owner owner = this.createOwner();
        String username = "username";

        assertNull(this.ueberCertificateCurator.findForOwner(owner));

        UeberCertificateGenerator generator = this.buildGenerator();
        UeberCertificate output = generator.generate(scheme, owner, username);

        assertThat(output)
            .isNotNull()
            .doesNotReturn(null, UeberCertificate::getCert)
            .doesNotReturn(null, UeberCertificate::getKey);

        // Verify we can fetch the ueber cert via curator
        assertThat(this.ueberCertificateCurator.findForOwner(owner))
            .isSameAs(output);

        // Verify the cert and key are of the intended scheme
        OidUtil oidUtil = CryptoUtil.getOidUtil();

        X509Certificate x509cert = CryptoUtil.extractCertificateFromContainer(output);
        String sigAlgorithmOid = oidUtil.getSignatureAlgorithmOid(scheme.signatureAlgorithm())
            .orElseThrow(() -> new RuntimeException("Unable to convert algorithm name to an OID"));

        assertEquals(sigAlgorithmOid, x509cert.getSigAlgOID());

        PrivateKey pkey = CryptoUtil.extractPrivateKeyFromContainer(output);
        String keyAlgorithmOid = oidUtil.getKeyAlgorithmOid(scheme.keyAlgorithm())
            .orElseThrow(() -> new RuntimeException("Unable to convert algorithm name to an OID"));

        String receivedKeyAlgorithmOid = oidUtil.getKeyAlgorithmOid(pkey.getAlgorithm())
            .orElseThrow(() -> new RuntimeException("Unable to convert algorithm name to an OID"));

        assertEquals(keyAlgorithmOid, receivedKeyAlgorithmOid);

        // Verify the cert was issued by the scheme's CA cert, and isn't self-signed
        assertThat(x509cert.getIssuerX500Principal())
            .isNotEqualTo(x509cert.getSubjectX500Principal())
            .isEqualTo(scheme.certificate().getSubjectX500Principal());

        // Verify the cert has the correct validity range. It needs to be active at the time of generation,
        // and up to December, 2049
        assertThat(x509cert.getNotBefore())
            .isNotNull()
            .isBefore(new Date());

        assertThat(x509cert.getNotAfter())
            .isNotNull()
            .isAfter(Date.from(OffsetDateTime.of(2049, 12, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant()));
    }

    // TODO: Add a test that validates the cert has the correct extensions and content >:(

    @Test
    public void testGenerateCertificateRequiresScheme() throws Exception {
        Owner owner = this.createOwner();
        String username = "username";

        UeberCertificateGenerator generator = this.buildGenerator();

        assertThrows(IllegalArgumentException.class, () -> generator.generate(null, owner, username));
    }

    @Test
    public void testGenerateCertificateRequiresOwner() throws Exception {
        Scheme scheme = CryptoUtil.SUPPORTED_SCHEMES.values().stream().findAny().get();
        String username = "username";

        UeberCertificateGenerator generator = this.buildGenerator();

        assertThrows(IllegalArgumentException.class, () -> generator.generate(scheme, null, username));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "\t", "\n" })
    public void testGenerateCertificateDoesNotRequireUsername(String username) throws Exception {
        // The username isn't guaranteed to be populated by every principal, and the existing implementation
        // performs no validation or checks on its presence. This test codifies this behavior to avoid any
        // regressions on this going forward.

        Scheme scheme = CryptoUtil.SUPPORTED_SCHEMES.values().stream().findAny().get();
        Owner owner = this.createOwner();

        UeberCertificateGenerator generator = this.buildGenerator();
        UeberCertificate output = generator.generate(scheme, owner, username);

        assertThat(output)
            .isNotNull()
            .doesNotReturn(null, UeberCertificate::getCert)
            .doesNotReturn(null, UeberCertificate::getKey);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGenerateCertificateRevokesPreviousOwnerUeberCert(Scheme scheme) throws Exception {
        // This test verifies that any previous ueber certificate for the owner is revoked as part of the
        // operation to generate a new one

        Owner owner = this.createOwner();
        String username = "username";

        UeberCertificateGenerator generator = this.buildGenerator();
        UeberCertificate first = generator.generate(scheme, owner, username);

        assertThat(this.ueberCertificateCurator.findForOwner(owner))
            .isNotNull()
            .isSameAs(first)
            .doesNotReturn(null, UeberCertificate::getCert)
            .doesNotReturn(null, UeberCertificate::getKey);

        // Generate a second cert for the same owner
        UeberCertificate second = generator.generate(scheme, owner, username);

        assertThat(second)
            .isNotNull()
            .isNotSameAs(first)
            .doesNotReturn(null, UeberCertificate::getCert)
            .doesNotReturn(first.getCert(), UeberCertificate::getCert)
            .doesNotReturn(null, UeberCertificate::getKey)
            .doesNotReturn(first.getKey(), UeberCertificate::getKey)
            .extracting(UeberCertificate::getSerial)
            .returns(false, CertificateSerial::isRevoked);

        assertThat(this.ueberCertificateCurator.findForOwner(owner))
            .isSameAs(second);

        assertThat(first)
            .extracting(UeberCertificate::getSerial)
            .returns(true, CertificateSerial::isRevoked);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGenerateCertificateDoesNotRevokeOtherOwnerCert(Scheme scheme) throws Exception {
        // This test verifies that any previous ueber certificate for the owner is revoked as part of the
        // operation to generate a new one

        UeberCertificateGenerator generator = this.buildGenerator();

        Owner owner1 = this.createOwner();
        String owner1User = "owner1_user";

        UeberCertificate owner1Cert = generator.generate(scheme, owner1, owner1User);
        assertThat(this.ueberCertificateCurator.findForOwner(owner1))
            .isNotNull()
            .isSameAs(owner1Cert)
            .doesNotReturn(null, UeberCertificate::getCert)
            .doesNotReturn(null, UeberCertificate::getKey);

        // Generate a new cert for a different owner
        Owner owner2 = this.createOwner();
        String owner2User = "owner2_user";

        UeberCertificate owner2Cert = generator.generate(scheme, owner2, owner2User);
        assertThat(owner2Cert)
            .isNotNull()
            .isNotSameAs(owner1Cert)
            .doesNotReturn(null, UeberCertificate::getCert)
            .doesNotReturn(owner1Cert.getCert(), UeberCertificate::getCert)
            .doesNotReturn(null, UeberCertificate::getKey)
            .doesNotReturn(owner1Cert.getKey(), UeberCertificate::getKey)
            .extracting(UeberCertificate::getSerial)
            .returns(false, CertificateSerial::isRevoked);

        // Verify that neither cert has been revoked
        assertThat(this.ueberCertificateCurator.findForOwner(owner1))
            .isSameAs(owner1Cert)
            .extracting(UeberCertificate::getSerial)
            .returns(false, CertificateSerial::isRevoked);

        assertThat(this.ueberCertificateCurator.findForOwner(owner2))
            .isSameAs(owner2Cert)
            .extracting(UeberCertificate::getSerial)
            .returns(false, CertificateSerial::isRevoked);
    }

}
