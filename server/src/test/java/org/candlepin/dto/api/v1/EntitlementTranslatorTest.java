/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Certificate;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;

import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Test suite for the EntitlementTranslator class.
 */
public class EntitlementTranslatorTest extends
    AbstractTranslatorTest<Entitlement, EntitlementDTO, EntitlementTranslator> {

    protected EntitlementTranslator translator = new EntitlementTranslator();

    private OwnerTranslatorTest ownerTranslatorTest = new OwnerTranslatorTest();
    private CertificateTranslatorTest certificateTranslatorTest = new CertificateTranslatorTest();
    private PoolTranslatorTest poolTranslatorTest = new PoolTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        this.ownerTranslatorTest.initModelTranslator(modelTranslator);
        this.certificateTranslatorTest.initModelTranslator(modelTranslator);
        this.poolTranslatorTest.initModelTranslator(modelTranslator);

        modelTranslator.registerTranslator(this.translator, Entitlement.class, EntitlementDTO.class);
    }

    @Override
    protected EntitlementTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected Entitlement initSourceObject() {
        Entitlement source = new Entitlement();
        source.setId("ent-id");
        source.setQuantity(1);
        source.setDeletedFromPool(false);

        source.setOwner(this.ownerTranslatorTest.initSourceObject());
        source.setPool(this.poolTranslatorTest.initSourceObject());

        HashSet<EntitlementCertificate> certs = new HashSet<EntitlementCertificate>();
        EntitlementCertificate entCert = new EntitlementCertificate();
        entCert.setId("ent-cert-id");
        entCert.setEntitlement(source);
        entCert.setKey("ent-cert-key");
        entCert.setCert("ent-cert-cert");
        entCert.setSerial(new CertificateSerial());
        certs.add(entCert);
        source.setCertificates(certs);

        //TODO: use ConsumerDTO once it gets introduced.
        Consumer consumer = new Consumer();
        consumer.setUuid("consumer-uuid");
        source.setConsumer(consumer);

        return source;
    }

    @Override
    protected EntitlementDTO initDestinationObject() {
        return new EntitlementDTO();
    }

    @Override
    protected void verifyOutput(Entitlement source, EntitlementDTO dest, boolean childrenGenerated) {
        if (source != null) {

            assertEquals(source.getId(), dest.getId());
            assertEquals(source.getQuantity(), dest.getQuantity());
            assertEquals(source.deletedFromPool(), dest.isDeletedFromPool());

            if (childrenGenerated) {
                this.ownerTranslatorTest
                    .verifyOutput(source.getOwner(), dest.getOwner(), true);

                this.poolTranslatorTest.verifyOutput(source.getPool(), dest.getPool(), true);

                // These getters' returned fields live on the pool, so they're children.
                assertEquals(source.getStartDate(), dest.getStartDate());
                assertEquals(source.getEndDate(), dest.getEndDate());

                for (Certificate sourceCertificate : source.getCertificates()) {
                    for (CertificateDTO certDTO : dest.getCertificates()) {

                        assertNotNull(certDTO);
                        assertNotNull(certDTO.getId());

                        if (certDTO.getId().equals(sourceCertificate.getId())) {
                            this.certificateTranslatorTest.verifyOutput(sourceCertificate, certDTO, true);
                        }
                    }
                }

                //TODO: use ConsumerTranslatorTest to verify once it gets introduced.
                assertEquals(source.getConsumer(), dest.getConsumer());
            }
            else {
                assertNull(dest.getOwner());
                assertNull(dest.getPool());
                assertNull(dest.getCertificates());
                assertNull(dest.getConsumer());
            }
        }
        else {
            assertNull(dest);
        }
    }
}
