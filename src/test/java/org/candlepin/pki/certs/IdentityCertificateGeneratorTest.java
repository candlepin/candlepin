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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import org.candlepin.config.Configuration;
import org.candlepin.config.TestConfig;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.Owner;
import org.candlepin.pki.CryptoCapabilitiesException;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.Scheme;
import org.candlepin.pki.util.ConsumerKeyPairGenerator;
import org.candlepin.test.CryptoUtil;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.security.KeyException;
import java.security.cert.CertificateException;
import java.util.stream.Stream;

public class IdentityCertificateGeneratorTest extends DatabaseTestFixture {
    private CryptoManager cryptoManager;

    private IdentityCertificateGenerator identityCertificateGenerator;

    @BeforeEach
    public void setUp() throws CertificateException, KeyException {
        Configuration config = TestConfig.defaults();

        this.cryptoManager = CryptoUtil.getCryptoManager(this.config);

        ConsumerKeyPairGenerator keyPairGenerator =
            new ConsumerKeyPairGenerator(cryptoManager, this.keyPairDataCurator);

        this.identityCertificateGenerator = new IdentityCertificateGenerator(config,
            this.cryptoManager,
            CryptoUtil.getPemEncoder(),
            keyPairGenerator,
            this.identityCertificateCurator,
            this.certSerialCurator);
    }

    @Test
    public void testGenerateWithNullConsumer() {
        assertThrows(IllegalArgumentException.class, () -> {
            this.identityCertificateGenerator.generate(null);
        });
    }

    @Test
    public void testGenerateWithExistingIdentityCertificate() throws Exception {
        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner);
        IdentityCertificate existing = this.createIdCert(consumer);

        IdentityCertificate actual = this.identityCertificateGenerator.generate(consumer);

        assertNotNull(actual);
        assertIdCert(existing, actual);
    }

    private static Stream<Arguments> schemeSource() {
        return CryptoUtil.SUPPORTED_SCHEMES.values()
            .stream()
            .map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGenerate(Scheme scheme) throws Exception {
        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumerWithScheme(owner, scheme);
        assertNull(consumer.getIdCert());

        IdentityCertificate actual = this.identityCertificateGenerator.generate(consumer);

        assertThat(actual)
            .isNotNull()
            .doesNotReturn(null, IdentityCertificate::getId)
            .doesNotReturn(null, IdentityCertificate::getKey)
            .doesNotReturn(null, IdentityCertificate::getCreated)
            .doesNotReturn(null, IdentityCertificate::getUpdated)
            .doesNotReturn(null, IdentityCertificate::getCertificate)
            .doesNotReturn(null, IdentityCertificate::getSerial);

        IdentityCertificate persistedCert = this.identityCertificateCurator.get(actual.getId());
        assertNotNull(persistedCert);
        assertIdCert(persistedCert, actual);

        CertificateSerial persistedSerial = this.certSerialCurator.get(actual.getSerial().getId());
        assertNotNull(persistedSerial);
        assertSerial(persistedSerial, actual.getSerial());

        // TODO: Assert that the correct scheme was used for generating the cert when the capability exists
    }

    @Test
    public void testGenerateWithNoConsumerScheme() throws Exception {
        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumerWithScheme(owner, null);
        assertNull(consumer.getIdCert());

        IdentityCertificate actual = this.identityCertificateGenerator.generate(consumer);

        // A certificate should be generated using the default scheme
        assertNotNull(actual);

        // TODO: Assert that the correct scheme was used for generating the cert when the capability exists
    }

    @Test
    public void testGenerateWithUnknownConsumerScheme() throws Exception {
        Owner owner = this.createOwner();
        ConsumerType consumerType = new ConsumerType(TestUtil.randomString("label-"))
            .setManifest(false);
        consumerType = this.consumerTypeCurator.create(consumerType);

        Consumer consumer = this.consumerCurator.create(new Consumer()
            .setName(TestUtil.randomString("name-"))
            .setUsername(TestUtil.randomString("username-"))
            .setOwner(owner)
            .setType(consumerType));

        CryptoUtil.configureConsumerWithNoSelectableScheme(consumer);

        assertThrows(CryptoCapabilitiesException.class, () -> {
            this.identityCertificateGenerator.generate(consumer);
        });
    }

    @Test
    public void testGenerateWithKeyException() throws Exception {
        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumerWithScheme(owner, null);
        assertNull(consumer.getIdCert());

        ConsumerKeyPairGenerator keyPairGenerator = mock(ConsumerKeyPairGenerator.class);
        doThrow(KeyException.class).when(keyPairGenerator).getConsumerKeyPair(consumer);
        IdentityCertificateGenerator generator = new IdentityCertificateGenerator(this.config,
            this.cryptoManager,
            CryptoUtil.getPemEncoder(),
            keyPairGenerator,
            this.identityCertificateCurator,
            this.certSerialCurator);

        assertThrows(CertificateCreationException.class, () -> generator.generate(consumer));
    }

    @Test
    public void testRegenerateWithNullConsumer() {
        assertThrows(IllegalArgumentException.class, () -> {
            this.identityCertificateGenerator.regenerate(null);
        });
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testRegenerateWithExistingIdentityCertificate(Scheme scheme) throws Exception {
        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumerWithScheme(owner, scheme);
        IdentityCertificate existing = this.createIdCert(consumer);

        IdentityCertificate actual = this.identityCertificateGenerator.regenerate(consumer);

        assertThat(actual)
            .isNotNull()
            .doesNotReturn(existing.getId(), IdentityCertificate::getId)
            .doesNotReturn(existing.getKey(), IdentityCertificate::getKey)
            .doesNotReturn(existing.getCreated(), IdentityCertificate::getCreated)
            .doesNotReturn(existing.getUpdated(), IdentityCertificate::getUpdated)
            .doesNotReturn(existing.getCertificate(), IdentityCertificate::getCertificate);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testRegenerate(Scheme scheme) throws Exception {
        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumerWithScheme(owner, scheme);
        assertNull(consumer.getIdCert());

        IdentityCertificate actual = this.identityCertificateGenerator.regenerate(consumer);

        assertThat(actual)
            .isNotNull()
            .doesNotReturn(null, IdentityCertificate::getId)
            .doesNotReturn(null, IdentityCertificate::getKey)
            .doesNotReturn(null, IdentityCertificate::getCreated)
            .doesNotReturn(null, IdentityCertificate::getUpdated)
            .doesNotReturn(null, IdentityCertificate::getCertificate)
            .doesNotReturn(null, IdentityCertificate::getSerial);

        IdentityCertificate persistedCert = this.identityCertificateCurator.get(actual.getId());
        assertNotNull(persistedCert);
        assertIdCert(persistedCert, actual);

        CertificateSerial persistedSerial = this.certSerialCurator.get(actual.getSerial().getId());
        assertNotNull(persistedSerial);
        assertSerial(persistedSerial, actual.getSerial());

        // TODO: Assert that the correct scheme was used for generating the cert when the capability exists
    }

    @Test
    public void testRegenerateWithNoConsumerScheme() throws Exception {
        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumerWithScheme(owner, null);
        assertNull(consumer.getIdCert());

        IdentityCertificate actual = this.identityCertificateGenerator.regenerate(consumer);

        // A certificate should be generated using the default scheme
        assertNotNull(actual);

        // TODO: Assert that the correct scheme was used for generating the cert when the capability exists
    }

    @Test
    public void testRegenerateWithUnknownConsumerScheme() throws Exception {
        Owner owner = this.createOwner();
        ConsumerType consumerType = new ConsumerType(TestUtil.randomString("label-"))
            .setManifest(false);
        consumerType = this.consumerTypeCurator.create(consumerType);

        Consumer consumer = this.consumerCurator.create(new Consumer()
            .setName(TestUtil.randomString("name-"))
            .setUsername(TestUtil.randomString("username-"))
            .setOwner(owner)
            .setType(consumerType));

        CryptoUtil.configureConsumerWithNoSelectableScheme(consumer);

        assertThrows(CryptoCapabilitiesException.class, () -> {
            this.identityCertificateGenerator.regenerate(consumer);
        });
    }

    @Test
    public void testRegenerateWithKeyException() throws Exception {
        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumerWithScheme(owner, null);
        assertNull(consumer.getIdCert());

        ConsumerKeyPairGenerator keyPairGenerator = mock(ConsumerKeyPairGenerator.class);
        doThrow(KeyException.class).when(keyPairGenerator).getConsumerKeyPair(consumer);
        IdentityCertificateGenerator generator = new IdentityCertificateGenerator(this.config,
            this.cryptoManager,
            CryptoUtil.getPemEncoder(),
            keyPairGenerator,
            this.identityCertificateCurator,
            this.certSerialCurator);

        assertThrows(CertificateCreationException.class, () -> generator.regenerate(consumer));
    }

    private Consumer createConsumerWithScheme(Owner owner, Scheme scheme) {
        ConsumerType consumerType = new ConsumerType(TestUtil.randomString("label-"))
            .setManifest(false);
        consumerType = this.consumerTypeCurator.create(consumerType);

        Consumer consumer = new Consumer()
            .setName(TestUtil.randomString("name-"))
            .setUsername(TestUtil.randomString("username-"))
            .setOwner(owner)
            .setType(consumerType);

        if (scheme != null) {
            CryptoUtil.configureConsumerForSchemes(consumer, scheme);
        }

        return this.consumerCurator.create(consumer);
    }

    private IdentityCertificate createIdCert(Consumer consumer) {
        CertificateSerial serial = this.createSerial(100);

        IdentityCertificate idCert = new IdentityCertificate();
        idCert.setKey(TestUtil.randomString("key-"));
        idCert.setCert(TestUtil.randomString("cert-"));
        idCert.setSerial(serial);

        idCert = this.identityCertificateCurator.create(idCert);

        consumer.setIdCert(idCert);
        this.consumerCurator.update(consumer);

        return idCert;
    }

    private CertificateSerial createSerial(int duration) {
        CertificateSerial serial = new CertificateSerial(TestUtil.createDateOffset(0, 0, duration));

        return this.certSerialCurator.create(serial);
    }

    private void assertIdCert(IdentityCertificate expected, IdentityCertificate actual) {
        assertThat(actual)
            .returns(expected.getId(), IdentityCertificate::getId)
            .returns(expected.getKey(), IdentityCertificate::getKey)
            .returns(expected.getCreated(), IdentityCertificate::getCreated)
            .returns(expected.getUpdated(), IdentityCertificate::getUpdated)
            .returns(expected.getCertificate(), IdentityCertificate::getCertificate);
    }

    private void assertSerial(CertificateSerial expected, CertificateSerial actual) {
        assertThat(actual)
            .returns(expected.getId(), CertificateSerial::getId)
            .returns(expected.getSerial(), CertificateSerial::getSerial)
            .returns(expected.getExpiration(), CertificateSerial::getExpiration)
            .returns(expected.getCreated(), CertificateSerial::getCreated)
            .returns(expected.getUpdated(), CertificateSerial::getUpdated);
    }

}
