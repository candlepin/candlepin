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
package org.candlepin.policy.js.compliance.hash;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.policy.SystemPurposeComplianceStatus;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.xnap.commons.i18n.I18n;

import java.util.HashSet;
import java.util.Set;

class SystemPurposeStatusHasherTest {

    private Owner owner;
    private Consumer consumer;

    @BeforeEach
    void setUp() {
        this.owner = new Owner()
            .setId("test-owner-id")
            .setKey("test-owner")
            .setDisplayName("Test Owner");

        this.consumer = createConsumer(this.owner);
    }

    @Test
    void shouldChangeWithUpdatedServiceLevel() {
        SystemPurposeComplianceStatus status = createStatus();
        String initialHash = hash(this.consumer, status);

        status.addCompliantSLA("Changed", createEntitlement());

        assertNotEquals(initialHash, hash(this.consumer, status));
    }

    @Test
    void shouldChangeWithUpdatedServiceType() {
        SystemPurposeComplianceStatus status = createStatus();
        String initialHash = hash(this.consumer, status);

        status.addCompliantServiceType("Changed", createEntitlement());

        assertNotEquals(initialHash, hash(this.consumer, status));
    }

    @Test
    void shouldChangeWithUpdatedRole() {
        SystemPurposeComplianceStatus status = createStatus();
        String initialHash = hash(this.consumer, status);

        status.addCompliantRole("Changed", createEntitlement());

        assertNotEquals(initialHash, hash(this.consumer, status));
    }

    @Test
    void shouldChangeWithUpdatedUsage() {
        SystemPurposeComplianceStatus status = createStatus();
        String initialHash = hash(this.consumer, status);

        status.addCompliantUsage("Changed", createEntitlement());

        assertNotEquals(initialHash, hash(this.consumer, status));
    }

    @Test
    void shouldChangeWithUpdatedAddons() {
        SystemPurposeComplianceStatus status = createStatus();
        String initialHash = hash(this.consumer, status);

        status.addCompliantAddOn("Changed", createEntitlement());

        assertNotEquals(initialHash, hash(this.consumer, status));
    }

    @Test
    void shouldChangeWithRemovedEntitlement() {
        SystemPurposeComplianceStatus status = createStatus();
        String initialHash = hash(this.consumer, status);

        this.consumer.setEntitlements(null);

        assertNotEquals(initialHash, hash(this.consumer, status));
    }

    @Test
    void shouldChangeWithAddedEntitlement() {
        SystemPurposeComplianceStatus status = createStatus();
        String initialHash = hash(this.consumer, status);

        this.consumer.addEntitlement(createEntitlement());

        assertNotEquals(initialHash, hash(this.consumer, status));
    }

    private String hash(Consumer consumer, SystemPurposeComplianceStatus status) {
        return new ComplianceStatusHasher(consumer, status).hash();
    }

    private Consumer createConsumer(Owner owner) {
        ConsumerType ctype = new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM);
        ctype.setId("test-ctype");

        Consumer consumer = new Consumer()
            .setUuid("12345")
            .setId("1")
            .setName("test-consumer")
            .setUsername("test-consumer")
            .setOwner(this.owner)
            .setType(ctype)
            .setServiceLevel("SLA")
            .setServiceType("L1-L3")
            .setRole("common_role")
            .setUsage("Development")
            .setAddOns(Set.of("RHEL EUS", "RHEL ELS"));

        Product product = TestUtil.createProduct("prod1");
        Pool pool = TestUtil.createPool(owner, product);
        consumer.setEntitlements(new HashSet<>(Set.of(
            TestUtil.createEntitlement(owner, consumer, pool, null),
            TestUtil.createEntitlement(owner, consumer, pool, null)
        )));

        return consumer;
    }

    private SystemPurposeComplianceStatus createStatus() {
        SystemPurposeComplianceStatus status = new SystemPurposeComplianceStatus(Mockito.mock(I18n.class));
        status.addCompliantSLA("SLA", createEntitlement());
        status.addCompliantServiceType("L1-L3", createEntitlement());
        status.addCompliantRole("common_role", createEntitlement());
        status.addCompliantUsage("Development", createEntitlement());
        status.addCompliantAddOn("RHEL EUS", createEntitlement());

        return status;
    }

    private Entitlement createEntitlement() {
        Product product = TestUtil.createProduct("prod1");
        Pool pool = TestUtil.createPool(this.owner, product);
        return TestUtil.createEntitlement(this.owner, this.consumer, pool, null);
    }

}
