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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.util.Util;

import java.util.Date;

/**
 * Test suite for the ConsumerInstalledProductTranslator class
 */
public class ConsumerInstalledProductTranslatorTest extends
    AbstractTranslatorTest<ConsumerInstalledProduct, ConsumerInstalledProductDTO,
    ConsumerInstalledProductTranslator> {

    protected ConsumerInstalledProductTranslator cipTranslator = new ConsumerInstalledProductTranslator();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.cipTranslator, ConsumerInstalledProduct.class,
            ConsumerInstalledProductDTO.class);
    }

    @Override
    protected ConsumerInstalledProductTranslator initObjectTranslator() {
        return this.cipTranslator;
    }

    @Override
    protected ConsumerInstalledProduct initSourceObject() {
        ConsumerInstalledProduct source = new ConsumerInstalledProduct();

        source.setId("test_id");
        source.setProductId("test_product_id");
        source.setProductName("test_product_name");
        source.setVersion("test_version");
        source.setArch("test_arch");
        source.setStatus("test_status");
        source.setStartDate(new Date());
        source.setEndDate(new Date());
        return source;
    }

    @Override
    protected ConsumerInstalledProductDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new ConsumerInstalledProductDTO();
    }

    @Override
    protected void verifyOutput(ConsumerInstalledProduct source, ConsumerInstalledProductDTO dto,
        boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getCreated(), dto.getCreated());
            assertEquals(source.getUpdated(), dto.getUpdated());
            assertEquals(source.getId(), dto.getId());
            assertEquals(source.getProductId(), dto.getProductId());
            assertEquals(source.getProductName(), dto.getProductName());
            assertEquals(source.getVersion(), dto.getVersion());
            assertEquals(source.getArch(), dto.getArch());
            assertEquals(source.getStatus(), dto.getStatus());
            assertEquals(source.getStartDate(), Util.toDate(dto.getStartDate()));
            assertEquals(source.getEndDate(), Util.toDate(dto.getEndDate()));
        }
        else {
            assertNull(dto);
        }
    }
}
