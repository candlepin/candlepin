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
import org.candlepin.model.PoolExportProjection;

import org.junit.jupiter.api.Test;

import java.util.Date;

class PoolExportProjectionTranslatorTest extends
    AbstractTranslatorTest<PoolExportProjection, SubscriptionDTO, PoolExportProjectionTranslator> {

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.translator, PoolExportProjection.class, SubscriptionDTO.class);
    }

    @Override
    protected PoolExportProjectionTranslator initObjectTranslator() {
        return new PoolExportProjectionTranslator();
    }

    @Override
    protected PoolExportProjection initSourceObject() {
        return new PoolExportProjection("pool-id", "product-uuid", "product-id", "subscription-id",
            new Date(3000L), new Date(4000L), new Date(1000L), new Date(2000L), "contract", "account");
    }

    @Override
    protected SubscriptionDTO initDestinationObject() {
        return new SubscriptionDTO();
    }

    @Override
    protected void verifyOutput(PoolExportProjection source, SubscriptionDTO destination, boolean childrenGenerated) {
        if (source == null) {
            assertNull(destination);
            return;
        }

        assertEquals(source.subscriptionId(), destination.getSubscriptionId());
        assertEquals(source.startDate(), destination.getStartDate());
        assertEquals(source.endDate(), destination.getEndDate());
        assertEquals(source.contractNumber(), destination.getContractNumber());
        assertEquals(source.accountNumber(), destination.getAccountNumber());
        assertEquals(source.productId(), destination.getProductId());
        assertEquals(source.created(), destination.getCreated());
        assertEquals(source.updated(), destination.getUpdated());
    }

    @Test
    void testPopulateClearsAbsentValues() {
        SubscriptionDTO destination = this.translator.translate(this.source);
        PoolExportProjection empty = new PoolExportProjection("pool-id", "product-uuid", "product-id", null,
            null, null, null, null, null, null);

        this.translator.populate(empty, destination);

        this.verifyOutput(empty, destination, false);
    }

}
