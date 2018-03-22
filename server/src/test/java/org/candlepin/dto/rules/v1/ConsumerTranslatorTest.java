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
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCapability;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;

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

    protected ConsumerTranslator translator;

    protected ConsumerTypeTranslatorTest consumerTypeTranslatorTest = new ConsumerTypeTranslatorTest();
    protected OwnerTranslatorTest ownerTranslatorTest = new OwnerTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        this.consumerTypeTranslatorTest.initModelTranslator(modelTranslator);
        this.ownerTranslatorTest.initModelTranslator(modelTranslator);

        this.mockConsumerTypeCurator = mock(ConsumerTypeCurator.class);
        this.translator = new ConsumerTranslator(this.mockConsumerTypeCurator);

        modelTranslator.registerTranslator(this.translator, Consumer.class, ConsumerDTO.class);
    }

    @Override
    protected ConsumerTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected Consumer initSourceObject() {
        ConsumerType ctype = this.consumerTypeTranslatorTest.initSourceObject();

        Consumer consumer = new Consumer();

        consumer.setUuid("consumer_uuid");
        consumer.setUsername("consumer_user_name");
        consumer.setServiceLevel("consumer_service_level");
        consumer.setOwner(this.ownerTranslatorTest.initSourceObject());
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

        when(mockConsumerTypeCurator.find(eq(ctype.getId()))).thenReturn(ctype);
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

                this.ownerTranslatorTest.verifyOutput(source.getOwner(), dest.getOwner(), childrenGenerated);
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
