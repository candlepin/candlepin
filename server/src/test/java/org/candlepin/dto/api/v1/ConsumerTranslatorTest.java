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
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
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
 * Test suite for the ConsumerTranslator class
 */
@RunWith(JUnitParamsRunner.class)
public class ConsumerTranslatorTest extends
    AbstractTranslatorTest<Consumer, ConsumerDTO, ConsumerTranslator> {

    protected ConsumerTypeCurator mockConsumerTypeCurator;
    protected EnvironmentCurator mockEnvironmentCurator;
    private OwnerCurator mockOwnerCurator;

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
    protected ConsumerTranslator initObjectTranslator() {
        this.certificateTranslatorTest.initObjectTranslator();
        this.consumerTypeTranslatorTest.initObjectTranslator();
        this.ownerTranslatorTest.initObjectTranslator();
        this.environmentTranslatorTest.initObjectTranslator();
        this.cipTranslatorTest.initObjectTranslator();
        this.capabilityTranslatorTest.initObjectTranslator();
        this.hypervisorIdTranslatorTest.initObjectTranslator();
        this.guestIdTranslatorTest.initObjectTranslator();

        this.mockConsumerTypeCurator = mock(ConsumerTypeCurator.class);
        this.mockEnvironmentCurator = mock(EnvironmentCurator.class);
        this.mockOwnerCurator = mock(OwnerCurator.class);

        this.translator = new ConsumerTranslator(this.mockConsumerTypeCurator, this.mockEnvironmentCurator,
            this.mockOwnerCurator);

        return this.translator;
    }

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

        modelTranslator.registerTranslator(this.translator, Consumer.class, ConsumerDTO.class);
    }

    @Override
    protected Consumer initSourceObject() {
        ConsumerType ctype = this.consumerTypeTranslatorTest.initSourceObject();
        Environment environment = this.environmentTranslatorTest.initSourceObject();
        Owner owner = this.ownerTranslatorTest.initSourceObject();
        when(mockOwnerCurator.findOwnerById(eq(owner.getId()))).thenReturn(owner);

        Consumer consumer = new Consumer();

        consumer.setId("consumer_id");
        consumer.setUuid("consumer_uuid");
        consumer.setName("consumer_name");
        consumer.setUsername("consumer_user_name");
        consumer.setEntitlementStatus("consumer_ent_status");
        consumer.setServiceLevel("consumer_service_level");
        consumer.setReleaseVer(new Release("releaseVer"));
        consumer.setOwner(owner);
        consumer.setEnvironment(environment);
        consumer.setEntitlementCount(0L);
        consumer.setLastCheckin(new Date());
        consumer.setCanActivate(Boolean.TRUE);
        consumer.setHypervisorId(hypervisorIdTranslatorTest.initSourceObject());
        consumer.setAutoheal(Boolean.TRUE);
        consumer.setRecipientOwnerKey("test_recipient_owner_key");
        consumer.setAnnotations("test_annotations");
        consumer.setContentAccessMode("test_content_access_mode");
        consumer.setIdCert((IdentityCertificate) this.certificateTranslatorTest.initSourceObject());
        consumer.setType(ctype);

        Map<String, String> facts = new HashMap<>();
        for (int i = 0; i < 5; ++i) {
            facts.put("fact-" + i, "value-" + i);
        }
        consumer.setFacts(facts);

        Set<ConsumerInstalledProduct> installedProducts = new HashSet<>();
        for (int i = 0; i < 5; ++i) {
            ConsumerInstalledProduct installedProduct = cipTranslatorTest.initSourceObject();
            installedProduct.setId("installedProduct-" + i);
            installedProducts.add(installedProduct);
        }
        consumer.setInstalledProducts(installedProducts);

        Set<ConsumerCapability> capabilities = new HashSet<>();
        for (int i = 0; i < 5; ++i) {
            ConsumerCapability capability = capabilityTranslatorTest.initSourceObject();
            capability.setName("capability-" + i);
            capabilities.add(capability);
        }
        consumer.setCapabilities(capabilities);

        Set<String> contentTags = new HashSet<>();
        for (int i = 0; i < 5; ++i) {
            contentTags.add("contentTag-" + i);
        }
        consumer.setContentTags(contentTags);

        List<GuestId> guestIds = new LinkedList<>();
        for (int i = 0; i < 5; ++i) {
            GuestId guestId = guestIdTranslatorTest.initSourceObject();
            guestId.setId("guestId-" + i);
            guestIds.add(guestId);
        }
        consumer.setGuestIds(guestIds);

        when(mockConsumerTypeCurator.get(eq(ctype.getId()))).thenReturn(ctype);
        when(mockConsumerTypeCurator.getConsumerType(eq(consumer))).thenReturn(ctype);

        when(mockEnvironmentCurator.get(eq(environment.getId()))).thenReturn(environment);
        when(mockEnvironmentCurator.getConsumerEnvironment(eq(consumer))).thenReturn(environment);

        return consumer;
    }

    @Override
    protected ConsumerDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new ConsumerDTO();
    }

    @Override
    protected void verifyOutput(Consumer source, ConsumerDTO dest, boolean childrenGenerated) {

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
                ConsumerType ctype = this.mockConsumerTypeCurator.getConsumerType(source);
                this.consumerTypeTranslatorTest.verifyOutput(ctype, dest.getType(), true);

                Environment environment = this.mockEnvironmentCurator.getConsumerEnvironment(source);
                this.environmentTranslatorTest.verifyOutput(environment, dest.getEnvironment(), true);

                assertEquals(source.getReleaseVer().getReleaseVer(), dest.getReleaseVersion());
                String destOwnerId = null;
                if (dest.getOwner() != null) {
                    destOwnerId = dest.getOwner().getId();
                }
                assertEquals(source.getOwnerId(), destOwnerId);
                this.hypervisorIdTranslatorTest.verifyOutput(source.getHypervisorId(), dest.getHypervisorId(),
                    childrenGenerated);

                this.certificateTranslatorTest.verifyOutput(source.getIdCert(),
                    dest.getIdCertificate(), true);

                if (source.getInstalledProducts() != null) {
                    for (ConsumerInstalledProduct cip : source.getInstalledProducts()) {
                        for (ConsumerInstalledProductDTO cipDTO : dest.getInstalledProducts()) {
                            assertNotNull(cip);
                            assertNotNull(cipDTO);
                            this.cipTranslatorTest.verifyOutput(cip, cipDTO, childrenGenerated);
                        }
                    }
                }
                else {
                    assertNull(dest.getInstalledProducts());
                }

                if (source.getCapabilities() != null) {
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
                }
                else {
                    assertNull(dest.getCapabilities());
                }

                assertEquals(0, dest.getGuestIds().size());
            }
            else {
                assertNull(dest.getReleaseVersion());
                assertNull(dest.getOwner());
                assertNull(dest.getEnvironment());
                assertNull(dest.getHypervisorId());
                assertNull(dest.getType());
                assertNull(dest.getIdCertificate());
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
