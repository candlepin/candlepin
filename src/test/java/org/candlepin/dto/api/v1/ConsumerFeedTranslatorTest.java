/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.ConsumerFeedDTO;
import org.candlepin.dto.api.server.v1.ConsumerFeedInstalledProductDTO;
import org.candlepin.model.ConsumerFeed;
import org.candlepin.model.ConsumerFeedInstalledProduct;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import java.util.Date;
import java.util.Map;
import java.util.Set;

public class ConsumerFeedTranslatorTest
    extends AbstractTranslatorTest<ConsumerFeed, ConsumerFeedDTO, ConsumerFeedTranslator> {

    protected ConsumerFeedTranslator translator = new ConsumerFeedTranslator();
    protected ConsumerFeedInstalledProductTranslatorTest installedProductTranslatorTest =
        new ConsumerFeedInstalledProductTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(translator, ConsumerFeed.class, ConsumerFeedDTO.class);

        this.installedProductTranslatorTest.initModelTranslator(modelTranslator);
    }

    @Override
    protected ConsumerFeedTranslator initObjectTranslator() {
        this.installedProductTranslatorTest.initObjectTranslator();

        return this.translator;
    }

    @Override
    protected ConsumerFeed initSourceObject() {
        Map<String, String> facts = Map.of("fact1", "value1", "fact2", "value2");
        ConsumerFeedInstalledProduct installedProduct = new ConsumerFeedInstalledProduct(
            TestUtil.randomString("productId"), TestUtil.randomString("productName"),
            TestUtil.randomString("1.0.0"));

        return new ConsumerFeed()
            .setId(TestUtil.randomString("id"))
            .setUuid(TestUtil.randomString("uuid"))
            .setName(TestUtil.randomString("name"))
            .setTypeId(TestUtil.randomString("typeId"))
            .setOwnerKey(TestUtil.randomString("ownerKey"))
            .setLastCheckin(new Date())
            .setGuestId(TestUtil.randomString("guestId"))
            .setHypervisorUuid(TestUtil.randomString("hypervisorUuid"))
            .setHypervisorName(TestUtil.randomString("hypervisorName"))
            .setServiceLevel(TestUtil.randomString("serviceLevel"))
            .setSyspurposeRole(TestUtil.randomString("syspurposeRole"))
            .setSyspurposeUsage(TestUtil.randomString("syspurposeUsage"))
            .setSyspurposeAddons(Set.of("addon1", "addon2"))
            .setFacts(facts)
            .setInstalledProducts(Set.of(installedProduct));
    }

    @Override
    protected ConsumerFeedDTO initDestinationObject() {
        return new ConsumerFeedDTO();
    }

    @Override
    protected void verifyOutput(ConsumerFeed source, ConsumerFeedDTO dest, boolean childrenGenerated) {
        assertEquals(source.getId(), dest.getId());
        assertEquals(source.getUuid(), dest.getUuid());
        assertEquals(source.getName(), dest.getName());
        assertEquals(source.getTypeId(), dest.getTypeId());
        assertEquals(source.getOwnerKey(), dest.getOwnerKey());
        assertEquals(source.getLastCheckin(), Util.toDate(dest.getLastCheckin()));
        assertEquals(source.getGuestId(), dest.getGuestId());
        assertEquals(source.getHypervisorUuid(), dest.getHypervisorUuid());
        assertEquals(source.getHypervisorName(), dest.getHypervisorName());
        assertEquals(source.getServiceLevel(), dest.getServiceLevel());
        assertEquals(source.getSyspurposeRole(), dest.getSyspurposeRole());
        assertEquals(source.getSyspurposeUsage(), dest.getSyspurposeUsage());
        assertEquals(source.getFacts(), dest.getFacts());
        assertEquals(source.getSyspurposeAddons(), dest.getSyspurposeAddons());

        if (childrenGenerated) {
            if (source.getInstalledProducts() == null) {
                assertNull(dest.getInstalledProducts());
            }
            else {
                for (ConsumerFeedInstalledProduct cip : source.getInstalledProducts()) {
                    for (ConsumerFeedInstalledProductDTO cipDTO : dest.getInstalledProducts()) {
                        assertNotNull(cip);
                        assertNotNull(cipDTO);
                        this.installedProductTranslatorTest.verifyOutput(cip, cipDTO, true);
                    }
                }

            }
        }
        else {
            assertNull(dest.getInstalledProducts());
        }
    }
}
