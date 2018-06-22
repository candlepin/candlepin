/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
package org.candlepin.dto.rules.v1;

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
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;

import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Test suite for the ConsumerTranslator (Rules framework) class
 */
@RunWith(JUnitParamsRunner.class)
public class ConsumerTranslatorTest extends
    AbstractTranslatorTest<Consumer, ConsumerDTO, ConsumerTranslator> {

    protected ConsumerTypeCurator mockConsumerTypeCurator;
    protected OwnerCurator mockOwnerCurator;

    protected ConsumerTypeTranslatorTest consumerTypeTranslatorTest = new ConsumerTypeTranslatorTest();
    protected OwnerTranslatorTest ownerTranslatorTest = new OwnerTranslatorTest();

    @Override
    protected ConsumerTranslator initObjectTranslator() {
        this.consumerTypeTranslatorTest.initObjectTranslator();
        this.ownerTranslatorTest.initObjectTranslator();

        this.mockConsumerTypeCurator = mock(ConsumerTypeCurator.class);
        this.mockOwnerCurator = mock(OwnerCurator.class);

        this.translator = new ConsumerTranslator(this.mockConsumerTypeCurator, this.mockOwnerCurator);
        return this.translator;
    }

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        this.consumerTypeTranslatorTest.initModelTranslator(modelTranslator);
        this.ownerTranslatorTest.initModelTranslator(modelTranslator);

        modelTranslator.registerTranslator(this.translator, Consumer.class, ConsumerDTO.class);
    }

    @Override
    protected Consumer initSourceObject() {
        ConsumerType ctype = this.consumerTypeTranslatorTest.initSourceObject();

        Consumer consumer = new Consumer();

        consumer.setUuid("consumer_uuid");
        consumer.setUsername("consumer_user_name");
        consumer.setServiceLevel("consumer_service_level");
        Owner owner = this.ownerTranslatorTest.initSourceObject();
        when(mockOwnerCurator.findOwnerById(eq(owner.getId()))).thenReturn(owner);
        consumer.setOwner(owner);
        consumer.setType(ctype);

        Map<String, String> facts = new HashMap<>();
        for (int i = 0; i < 5; ++i) {
            facts.put("fact-" + i, "value-" + i);
        }
        consumer.setFacts(facts);

        Set<ConsumerInstalledProduct> installedProducts = new HashSet<>();
        for (int i = 0; i < 5; ++i) {
            ConsumerInstalledProduct installedProduct = new ConsumerInstalledProduct();
            installedProduct.setProductId("installedProduct-" + i);
            installedProducts.add(installedProduct);
        }
        consumer.setInstalledProducts(installedProducts);

        Set<ConsumerCapability> capabilities = new HashSet<>();
        for (int i = 0; i < 5; ++i) {
            ConsumerCapability capability = new ConsumerCapability();
            capability.setName("capability-" + i);
            capabilities.add(capability);
        }
        consumer.setCapabilities(capabilities);

        when(mockConsumerTypeCurator.get(eq(ctype.getId()))).thenReturn(ctype);
        when(mockConsumerTypeCurator.getConsumerType(eq(consumer))).thenReturn(ctype);

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
            assertEquals(source.getUuid(), dest.getUuid());
            assertEquals(source.getUsername(), dest.getUsername());
            assertEquals(source.getServiceLevel(), dest.getServiceLevel());
            assertEquals(source.getFacts(), dest.getFacts());
            assertEquals(source.getCreated(), dest.getCreated());
            assertEquals(source.getUpdated(), dest.getUpdated());

            if (childrenGenerated) {
                ConsumerType ctype = this.mockConsumerTypeCurator.getConsumerType(source);

                assertEquals(source.getOwnerId(), dest.getOwner().getId());
                this.consumerTypeTranslatorTest.verifyOutput(ctype, dest.getType(), true);

                if (source.getInstalledProducts() != null) {
                    for (ConsumerInstalledProduct cip : source.getInstalledProducts()) {
                        boolean verified = false;
                        for (String cipDTO : dest.getInstalledProducts()) {
                            assertNotNull(cip);
                            assertNotNull(cipDTO);
                            if (cip.getProductId().contentEquals(cipDTO)) {
                                verified = true;
                            }
                        }
                        assertTrue(verified);
                    }
                }
                else {
                    assertNull(dest.getInstalledProducts());
                }

                if (source.getCapabilities() != null) {
                    for (ConsumerCapability cc : source.getCapabilities()) {
                        boolean verified = false;
                        for (String ccDTO : dest.getCapabilities()) {
                            assertNotNull(cc);
                            assertNotNull(ccDTO);
                            if (cc.getName().contentEquals(ccDTO)) {
                                verified = true;
                            }
                        }
                        assertTrue(verified);
                    }
                }
                else {
                    assertNull(dest.getCapabilities());
                }

            }
            else {
                assertNull(dest.getOwner());
                assertNull(dest.getType());
                assertNull(dest.getInstalledProducts());
                assertNull(dest.getCapabilities());
            }
        }
        else {
            assertNull(dest);
        }
    }
}
