/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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


package org.candlepin.dto.manifest.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;

import java.util.Date;

class SubscriptionTranslatorTest extends AbstractTranslatorTest<Pool, SubscriptionDTO, SubscriptionTranslator> {

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.translator, Pool.class, SubscriptionDTO.class);
    }

    @Override
    protected SubscriptionTranslator initObjectTranslator() {
        return new SubscriptionTranslator();
    }

    @Override
    protected Pool initSourceObject() {
        return new Pool()
            .setProduct(new Product("product-id", "product-name"))
            .setSubscriptionId("subscription-id")
            .setStartDate(new Date(1000L))
            .setEndDate(new Date(2000L))
            .setAccountNumber("account")
            .setContractNumber("contract")
            .setCreated(new Date(3000L))
            .setUpdated(new Date(4000L));
    }

    @Override
    protected SubscriptionDTO initDestinationObject() {
        return new SubscriptionDTO();
    }

    @Override
    protected void verifyOutput(Pool source, SubscriptionDTO destination, boolean childrenGenerated) {
        if (source == null) {
            assertNull(destination);
            return;
        }

        assertEquals(source.getSubscriptionId(), destination.getSubscriptionId());
        assertEquals(source.getStartDate(), destination.getStartDate());
        assertEquals(source.getEndDate(), destination.getEndDate());
        assertEquals(source.getContractNumber(), destination.getContractNumber());
        assertEquals(source.getAccountNumber(), destination.getAccountNumber());
        assertEquals(source.getProductId(), destination.getProductId());
        assertEquals(source.getCreated(), destination.getCreated());
        assertEquals(source.getUpdated(), destination.getUpdated());
    }
}
