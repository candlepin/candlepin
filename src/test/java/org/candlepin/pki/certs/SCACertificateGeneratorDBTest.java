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
package org.candlepin.pki.certs;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.controller.ContentAccessMode;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Consumer;
import org.candlepin.model.Environment;
import org.candlepin.model.Owner;
import org.candlepin.model.SCACertificate;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.util.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;


/**
 * Test suite for the ContentAccessManager backed by the testing database infrastructure
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SCACertificateGeneratorDBTest extends DatabaseTestFixture {
    private static final String ENTITLEMENT_MODE = ContentAccessMode.ENTITLEMENT.toDatabaseValue();
    private static final String ORG_ENVIRONMENT_MODE = ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

    private SCACertificateGenerator scaCertificateGenerator;

    @BeforeEach
    public void setup() throws Exception {
        this.scaCertificateGenerator = this.injector
            .getInstance(SCACertificateGenerator.class);
    }

    @Test
    public void testGetCertificate() {
        Owner owner = this.createSCAOwner();
        Consumer consumer = this.createV3Consumer(owner, null);

        this.scaCertGenerationTest(consumer);
    }

    @Test
    public void testGetCertificateWithEnvironment() {
        Owner owner = this.createSCAOwner();
        Environment environment = this.createEnvironment(owner);
        Consumer consumer = this.createV3Consumer(owner, environment);

        this.scaCertGenerationTest(consumer);
    }

    @Test
    public void testGetCertificateRegeneratesExpiredCerts() {
        Owner owner = this.createSCAOwner();
        Consumer consumer = this.createV3Consumer(owner, null);

        this.regenerateExpiredSCACertTest(consumer);
    }

    @Test
    public void testGetCertificateRegeneratesExpiredCertsWithEnvironment() {
        Owner owner = this.createSCAOwner();
        Environment environment = this.createEnvironment(owner);
        Consumer consumer = this.createV3Consumer(owner, environment);

        this.regenerateExpiredSCACertTest(consumer);
    }

    private void regenerateExpiredSCACertTest(Consumer consumer) {
        SCACertificateGenerator generator = this.scaCertificateGenerator;

        SCACertificate cert = generator.generate(consumer);
        String oldCert = cert.getCert();

        assertNotNull(cert);
        assertNotNull(consumer.getContentAccessCert());

        // Expire the cert, then run through the cycle again.
        SCACertificate consumerCert = consumer.getContentAccessCert();
        CertificateSerial oldSerial = consumerCert.getSerial();

        oldSerial.setExpiration(Util.yesterday());
        oldSerial = this.certSerialCurator.merge(oldSerial);

        // This should trigger a new cert to be generated which should end up with
        // a different serial than the one we have above.
        SCACertificate newCert = generator.generate(consumer);

        assertNotNull(newCert);
        assertNotNull(consumer.getContentAccessCert());

        assertNotEquals(newCert.getCert(), oldCert);
        assertNotEquals(newCert.getSerial().getId(), oldSerial.getId());
    }

    private Owner createSCAOwner() {
        Owner owner = this.createOwner();

        owner.setContentAccessModeList(ENTITLEMENT_MODE + ", " + ORG_ENVIRONMENT_MODE);
        owner.setContentAccessMode(ORG_ENVIRONMENT_MODE);

        return this.ownerCurator.merge(owner);
    }

    private Consumer createV3Consumer(Owner owner, Environment environment) {
        Consumer consumer = this.createConsumer(owner);

        consumer.setFact("system.certificate_version", "3.0");
        consumer.addEnvironment(environment);

        return this.consumerCurator.merge(consumer);
    }

    private void scaCertGenerationTest(Consumer consumer) {
        SCACertificateGenerator generator = this.scaCertificateGenerator;

        SCACertificate cert = generator.generate(consumer);

        assertNotNull(cert);
        assertNotNull(consumer.getContentAccessCert());
    }

}
