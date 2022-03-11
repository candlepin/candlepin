/**
 * Copyright (c) 2009 - 2021 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import org.candlepin.audit.EventSink;
import org.candlepin.controller.ContentAccessManager.ContentAccessMode;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Consumer;
import org.candlepin.model.ContentAccessCertificate;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.KeyPairDataCurator;
import org.candlepin.model.Owner;
import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.PrivateKeyReader;
import org.candlepin.pki.SubjectKeyIdentifierWriter;
import org.candlepin.pki.impl.DefaultSubjectKeyIdentifierWriter;
import org.candlepin.pki.impl.JSSPKIUtility;
import org.candlepin.pki.impl.JSSPrivateKeyReader;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.util.Util;
import org.candlepin.util.X509V3ExtensionUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.inject.Inject;



/**
 * Test suite for the ContentAccessManager backed by the testing database infrastructure
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ContentAccessManagerDBTest extends DatabaseTestFixture {

    private static final String ENTITLEMENT_MODE = ContentAccessMode.ENTITLEMENT.toDatabaseValue();
    private static final String ORG_ENVIRONMENT_MODE = ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

    @Inject private ContentAccessCertificateCurator caCertCurator;
    @Inject private KeyPairDataCurator keyPairDataCurator;

    private PKIUtility pkiUtility;
    private ObjectMapper objMapper;
    private X509V3ExtensionUtil x509V3ExtensionUtil;

    private EventSink mockEventSink;


    @BeforeEach
    public void setup() throws Exception {
        PrivateKeyReader keyReader = new JSSPrivateKeyReader();
        CertificateReader certReader = new CertificateReader(this.config, keyReader);
        SubjectKeyIdentifierWriter keyIdWriter = new DefaultSubjectKeyIdentifierWriter();
        this.pkiUtility = spy(new JSSPKIUtility(certReader, keyIdWriter, this.config,
            this.keyPairDataCurator));

        this.objMapper = new ObjectMapper();
        this.x509V3ExtensionUtil = spy(new X509V3ExtensionUtil(this.config, this.entitlementCurator,
            this.objMapper));

        this.mockEventSink = mock(EventSink.class);
    }

    private ContentAccessManager createManager() {
        return new ContentAccessManager(this.config, this.pkiUtility, this.x509V3ExtensionUtil,
            this.caCertCurator, this.certSerialCurator, this.ownerCurator, this.ownerContentCurator,
            this.consumerCurator, this.consumerTypeCurator, this.environmentCurator, this.caCertCurator,
            this.mockEventSink);
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


    private void scaCertGenerationTest(Consumer consumer) throws Exception {
        ContentAccessManager manager = this.createManager();

        ContentAccessCertificate cert = manager.getCertificate(consumer);

        assertNotNull(cert);
        assertNotNull(consumer.getContentAccessCert());
    }

    @Test
    public void testGetCertificate() throws Exception {
        Owner owner = this.createSCAOwner();
        Consumer consumer = this.createV3Consumer(owner, null);

        this.scaCertGenerationTest(consumer);
    }

    @Test
    public void testGetCertificateWithEnvironment() throws Exception {
        Owner owner = this.createSCAOwner();
        Environment environment = this.createEnvironment(owner);
        Consumer consumer = this.createV3Consumer(owner, environment);

        this.scaCertGenerationTest(consumer);
    }

    private void regenerateExpiredSCACertTest(Consumer consumer) throws Exception {
        ContentAccessManager manager = this.createManager();

        ContentAccessCertificate cert = manager.getCertificate(consumer);
        String oldCert = cert.getCert();

        assertNotNull(cert);
        assertNotNull(consumer.getContentAccessCert());

        // Expire the cert, then run through the cycle again.
        ContentAccessCertificate consumerCert = consumer.getContentAccessCert();
        CertificateSerial oldSerial = consumerCert.getSerial();

        oldSerial.setExpiration(Util.yesterday());
        oldSerial = this.certSerialCurator.merge(oldSerial);

        // This should trigger a new cert to be generated which should end up with
        // a different serial than the one we have above.
        ContentAccessCertificate newCert = manager.getCertificate(consumer);

        assertNotNull(newCert);
        assertNotNull(consumer.getContentAccessCert());

        assertNotEquals(newCert.getCert(), oldCert);
        assertNotEquals(newCert.getSerial().getId(), oldSerial.getId());
    }

    @Test
    public void testGetCertificateRegeneratesExpiredCerts() throws Exception {
        Owner owner = this.createSCAOwner();
        Consumer consumer = this.createV3Consumer(owner, null);

        this.regenerateExpiredSCACertTest(consumer);
    }

    @Test
    public void testGetCertificateRegeneratesExpiredCertsWithEnvironment() throws Exception {
        Owner owner = this.createSCAOwner();
        Environment environment = this.createEnvironment(owner);
        Consumer consumer = this.createV3Consumer(owner, environment);

        this.regenerateExpiredSCACertTest(consumer);
    }

}
