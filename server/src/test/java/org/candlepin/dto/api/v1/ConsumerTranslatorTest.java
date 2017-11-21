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

import static org.junit.Assert.*;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.GuestId;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.Release;

import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test suite for the UpstreamConsumerTranslator class
 */
@RunWith(JUnitParamsRunner.class)
public class ConsumerTranslatorTest extends
    AbstractTranslatorTest<Consumer, ConsumerDTO, ConsumerTranslator> {

    protected ConsumerTranslator translator = new ConsumerTranslator();

    protected CertificateTranslatorTest certificateTranslatorTest = new CertificateTranslatorTest();
    protected ConsumerTypeTranslatorTest consumerTypeTranslatorTest = new ConsumerTypeTranslatorTest();
    protected OwnerTranslatorTest ownerTranslatorTest = new OwnerTranslatorTest();
    protected EnvironmentTranslatorTest environmentTranslatorTest = new EnvironmentTranslatorTest();
    protected ConsumerInstalledProductTranslatorTest cipTranslatorTest =
        new ConsumerInstalledProductTranslatorTest();
    protected CapabilityTranslatorTest capabilityTranslatorTest = new CapabilityTranslatorTest();
    protected HypervisorIdTranslatorTest hypervisorIdTranslatorTest = new HypervisorIdTranslatorTest();
    protected GuestIdTranslatorTest guestIdTranslatorTest = new GuestIdTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        this.certificateTranslatorTest.initModelTranslator(modelTranslator);
        this.consumerTypeTranslatorTest.initModelTranslator(modelTranslator);
        this.ownerTranslatorTest.initModelTranslator(modelTranslator);
        this.environmentTranslatorTest.initModelTranslator(modelTranslator);
        this.cipTranslatorTest.initModelTranslator(modelTranslator);
        this.capabilityTranslatorTest.initModelTranslator(modelTranslator);
        this.hypervisorIdTranslatorTest.initModelTranslator(modelTranslator);
        this.guestIdTranslatorTest.initModelTranslator(modelTranslator);

        modelTranslator.registerTranslator(
            this.translator, Consumer.class, ConsumerDTO.class);
    }

    @Override
    protected ConsumerTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected Consumer initSourceObject() {
        Consumer consumer = new Consumer();

        consumer.setId("consumer_id");
        consumer.setUuid("consumer_uuid");
        consumer.setName("consumer_name");
        consumer.setUsername("consumer_user_name");
        consumer.setEntitlementStatus("consumer_ent_status");
        consumer.setServiceLevel("consumer_service_level");
        consumer.setReleaseVer(new Release("releaseVer"));
        consumer.setOwner(this.ownerTranslatorTest.initSourceObject());
        consumer.setEnvironment(this.environmentTranslatorTest.initSourceObject());
        consumer.setEntitlementCount(0L);
        consumer.setLastCheckin(new Date());
        consumer.setCanActivate(Boolean.TRUE);
        consumer.setHypervisorId(hypervisorIdTranslatorTest.initSourceObject());
        consumer.setAutoheal(Boolean.TRUE);
        consumer.setRecipientOwnerKey("test_recipient_owner_key");
        consumer.setAnnotations("test_annotations");
        consumer.setContentAccessMode("test_content_access_mode");
        consumer.setType(this.consumerTypeTranslatorTest.initSourceObject());
        consumer.setIdCert((IdentityCertificate) this.certificateTranslatorTest.initSourceObject());

        Map<String, String> facts = new HashMap<String, String>();
        for (int i = 0; i < 5; ++i) {
            facts.put("fact-" + i, "value-" + i);
        }
        consumer.setFacts(facts);

        Set<ConsumerInstalledProduct> installedProducts = new HashSet<ConsumerInstalledProduct>();
        for (int i = 0; i < 5; ++i) {
            ConsumerInstalledProduct installedProduct = cipTranslatorTest.initSourceObject();
            installedProduct.setId("installedProduct-" + i);
            installedProducts.add(installedProduct);
        }
        consumer.setInstalledProducts(installedProducts);

        Set<ConsumerCapability> capabilities = new HashSet<ConsumerCapability>();
        for (int i = 0; i < 5; ++i) {
            ConsumerCapability capability = capabilityTranslatorTest.initSourceObject();
            capability.setName("capability-" + i);
            capabilities.add(capability);
        }
        consumer.setCapabilities(capabilities);

        Set<String> contentTags = new HashSet<String>();
        for (int i = 0; i < 5; ++i) {
            contentTags.add("contentTag-" + i);
        }
        consumer.setContentTags(contentTags);

        List<GuestId> guestIds = new LinkedList<GuestId>();
        for (int i = 0; i < 5; ++i) {
            GuestId guestId = guestIdTranslatorTest.initSourceObject();
            guestId.setId("guestId-" + i);
            guestIds.add(guestId);
        }
        consumer.setGuestIds(guestIds);

        return consumer;
    }

    @Override
    protected ConsumerDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new ConsumerDTO();
    }

    @Override
    protected void verifyOutput(Consumer source, ConsumerDTO dest,
        boolean childrenGenerated) {

        if (source != null) {
            assertEquals(source.getId(), dest.getId());
            assertEquals(source.getUuid(), dest.getUuid());
            assertEquals(source.getName(), dest.getName());
            assertEquals(source.getUsername(), dest.getUsername());
            assertEquals(source.getEntitlementStatus(), dest.getEntitlementStatus());
            assertEquals(source.getServiceLevel(), dest.getServiceLevel());
            assertEquals(source.getEntitlementCount(), (long) dest.getEntitlementCount());
            assertEquals(source.getFacts(), dest.getFacts());
            assertEquals(source.getLastCheckin(), dest.getLastCheckin());
            assertEquals(source.isCanActivate(), dest.isCanActivate());
            assertEquals(source.getContentTags(), dest.getContentTags());
            assertEquals(source.isAutoheal(), dest.getAutoheal());
            assertEquals(source.getRecipientOwnerKey(), dest.getRecipientOwnerKey());
            assertEquals(source.getAnnotations(), dest.getAnnotations());
            assertEquals(source.getContentAccessMode(), dest.getContentAccessMode());


            if (childrenGenerated) {

                assertEquals(source.getReleaseVer().getReleaseVer(), dest.getReleaseVersion());
                this.ownerTranslatorTest.verifyOutput(source.getOwner(), dest.getOwner(), childrenGenerated);
                this.environmentTranslatorTest.verifyOutput(source.getEnvironment(), dest.getEnvironment(),
                    childrenGenerated);
                this.hypervisorIdTranslatorTest.verifyOutput(source.getHypervisorId(), dest.getHypervisorId
                    (), childrenGenerated);
                this.consumerTypeTranslatorTest
                    .verifyOutput(source.getType(), dest.getType(), true);
                this.certificateTranslatorTest
                    .verifyOutput(source.getIdCert(), dest.getIdCert(), true);

                for (ConsumerInstalledProduct cip : source.getInstalledProducts()) {
                    for (ConsumerInstalledProductDTO cipDTO : dest.getInstalledProducts()) {
                        assertNotNull(cip);
                        assertNotNull(cipDTO);
                        this.cipTranslatorTest.verifyOutput(cip, cipDTO, childrenGenerated);
                    }
                }

                for (ConsumerCapability cc : source.getCapabilities()) {
                    boolean verified = false;
                    for (CapabilityDTO ccDTO : dest.getCapabilities()) {
                        assertNotNull(cc);
                        assertNotNull(ccDTO);
                        if (cc.getName().contentEquals(ccDTO.getName())) {
                            this.capabilityTranslatorTest.verifyOutput(cc, ccDTO, childrenGenerated);
                            verified = true;
                        }
                    }
                    assertTrue(verified);
                }

                assertEquals(0, dest.getGuestIds().size());
            }
            else {
                assertNull(dest.getReleaseVersion());
                assertNull(dest.getOwner());
                assertNull(dest.getEnvironment());
                assertNull(dest.getHypervisorId());
                assertNull(dest.getType());
                assertNull(dest.getIdCert());
                assertNull(dest.getInstalledProducts());
                assertNull(dest.getCapabilities());
                assertNull(dest.getGuestIds());
            }
        }
        else {
            assertNull(dest);
        }
    }
}
