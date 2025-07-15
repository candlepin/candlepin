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
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.ConsumerFeedInstalledProductDTO;
import org.candlepin.model.ConsumerFeedInstalledProduct;
import org.candlepin.test.TestUtil;

public class ConsumerFeedInstalledProductTranslatorTest
    extends AbstractTranslatorTest<ConsumerFeedInstalledProduct, ConsumerFeedInstalledProductDTO,
    ConsumerFeedInstalledProductTranslator> {

    protected ConsumerFeedInstalledProductTranslator translator =
        new ConsumerFeedInstalledProductTranslator();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.translator, ConsumerFeedInstalledProduct.class,
            ConsumerFeedInstalledProductDTO.class);
    }

    @Override
    protected ConsumerFeedInstalledProductTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected ConsumerFeedInstalledProduct initSourceObject() {
        return new ConsumerFeedInstalledProduct(TestUtil.randomString("prodId"),
            TestUtil.randomString("prodName"), TestUtil.randomString("1.0.0"));
    }

    @Override
    protected ConsumerFeedInstalledProductDTO initDestinationObject() {
        return new ConsumerFeedInstalledProductDTO();
    }

    @Override
    protected void verifyOutput(ConsumerFeedInstalledProduct source, ConsumerFeedInstalledProductDTO dest,
        boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.productId(), dest.getProductId());
            assertEquals(source.productName(), dest.getProductName());
            assertEquals(source.productVersion(), dest.getProductVersion());
        }
        else {
            assertNull(dest);
        }
    }
}
