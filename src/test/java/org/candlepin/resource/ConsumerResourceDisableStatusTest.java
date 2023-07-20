/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.dto.api.server.v1.ComplianceStatusDTO;
import org.candlepin.dto.api.server.v1.SystemPurposeComplianceStatusDTO;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;



/**
 * ConsumerResourceEntitlementRulesTest
 */
public class ConsumerResourceDisableStatusTest extends DatabaseTestFixture {
    private ConsumerResource consumerResource;

    private Consumer consumer;

    private Owner owner;

    @BeforeEach
    public void setUp() {
        consumerResource = injector.getInstance(ConsumerResource.class);
        ConsumerType standardSystemType = consumerTypeCurator.create(new ConsumerType("standard-system"));

        this.owner = this.ownerCurator.create(new Owner()
            .setKey("test-owner")
            .setDisplayName("test-owner")
            .setContentAccessMode("org_environment"));

        Product product = this.createProduct(owner);

        Pool pool = createPool(owner, product, 10L, TestUtil.createDate(2010, 1, 1),
            TestUtil.createDate(2020, 12, 31));
        poolCurator.create(pool);

        consumer = TestUtil.createConsumer(standardSystemType, owner);
        consumer.setRole("myrole");
        consumer.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId(product.getId())
            .setProductName(product.getName()));

        consumerCurator.create(consumer);
    }

    @Test
    public void complianceStatusWhenGoldenTicketEnabled() {
        ComplianceStatusDTO status = consumerResource.getComplianceStatus(consumer.getUuid(), null);
        assertEquals("disabled", status.getStatus());
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals("gray", consumerResource.getConsumer(consumer.getUuid())
            .getInstalledProducts().iterator().next().getStatus());
    }

    @Test
    public void complianceStatusWhenGoldenTicketDisabled() {
        owner.setContentAccessMode("entitlement");
        ownerCurator.merge(owner);
        ComplianceStatusDTO status = consumerResource.getComplianceStatus(consumer.getUuid(), null);
        assertEquals("invalid", status.getStatus());
        assertEquals(1, status.getNonCompliantProducts().size());
        assertEquals("red", consumerResource.getConsumer(consumer.getUuid())
            .getInstalledProducts().iterator().next().getStatus());
    }

    @Test
    public void complianceStatusWhenGoldenTicketReenabled() {
        owner.setContentAccessMode("entitlement");
        ownerCurator.merge(owner);
        ComplianceStatusDTO status = consumerResource.getComplianceStatus(consumer.getUuid(), null);
        assertEquals("invalid", status.getStatus());

        owner.setContentAccessMode("org_environment");
        ownerCurator.merge(owner);
        status = consumerResource.getComplianceStatus(consumer.getUuid(), null);
        assertEquals("disabled", status.getStatus());
        assertEquals(0, status.getNonCompliantProducts().size());
        assertEquals("gray", consumerResource.getConsumer(consumer.getUuid())
            .getInstalledProducts().iterator().next().getStatus());
    }

    @Test
    public void systemPurposeStatusWhenGoldenTicketEnabled() {
        SystemPurposeComplianceStatusDTO status = consumerResource
            .getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertEquals("disabled", status.getStatus());
        assertNull(status.getNonCompliantRole());
    }

    @Test
    public void systemPurposeStatusWhenGoldenTicketDisabled() {
        owner.setContentAccessMode("entitlement");
        ownerCurator.merge(owner);
        SystemPurposeComplianceStatusDTO status = consumerResource
            .getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertEquals("mismatched", status.getStatus());
        assertEquals("myrole", status.getNonCompliantRole());
    }

    @Test
    public void systemPurposeStatusWhenGoldenTicketReenabled() {
        owner.setContentAccessMode("entitlement");
        ownerCurator.merge(owner);
        SystemPurposeComplianceStatusDTO status = consumerResource
            .getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertEquals("mismatched", status.getStatus());

        owner.setContentAccessMode("org_environment");
        ownerCurator.merge(owner);
        status = consumerResource.getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertEquals("disabled", status.getStatus());
        assertNull(status.getNonCompliantRole());
    }
}
