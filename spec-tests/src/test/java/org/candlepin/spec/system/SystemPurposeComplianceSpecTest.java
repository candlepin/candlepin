/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

package org.candlepin.spec.system;

import static org.assertj.core.api.Assertions.assertThat;

import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.SystemPurposeComplianceStatusDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Set;

@SpecTest
public class SystemPurposeComplianceSpecTest {

    private ApiClient adminClient;
    private OwnerDTO owner;
    private ApiClient userClient;

    private static final OffsetDateTime YEAR_IN_FUTURE = OffsetDateTime.now().plusYears(1);
    private static final OffsetDateTime YEAR_AND_HALF_IN_FUTURE = OffsetDateTime.now().plusYears(1)
        .plusMonths(6);
    private static final OffsetDateTime TWO_YEAR_IN_FUTURE = OffsetDateTime.now().plusYears(2);

    @BeforeEach
    public void beforeAll() {
        adminClient = ApiClients.admin();
        owner = adminClient.owners().createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(adminClient, owner);
        userClient = ApiClients.basic(user);
    }

    @Test
    public void shouldBeNotSpecifiedForAConsumerSansPurposePreference() {
        ConsumerDTO consumer = userClient.consumers().createConsumer(
            Consumers.random(owner).type(ConsumerTypes.System.value()));
        SystemPurposeComplianceStatusDTO status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status)
            .isNotNull()
            .returns("not specified", SystemPurposeComplianceStatusDTO::getStatus);
    }

    @Test
    @SuppressWarnings("checkstyle:indentation")
    public void shouldBeMismatchedForUnsatisfiedRole() {
        ConsumerDTO consumer = userClient.consumers().createConsumer(
            Consumers.random(owner).type(ConsumerTypes.System.value()).role("unsatisfied-role"));
        SystemPurposeComplianceStatusDTO status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status)
            .isNotNull()
            .returns("mismatched", SystemPurposeComplianceStatusDTO::getStatus)
            .returns("unsatisfied-role", SystemPurposeComplianceStatusDTO::getNonCompliantRole)
            .extracting(SystemPurposeComplianceStatusDTO::getReasons)
            .isEqualTo(Set.of("The requested role \"unsatisfied-role\" is not provided by a currently" +
                " consumed subscription."));
    }

    @Test
    public void shouldBeMatchedOnAFutureDateForAFutureEntitlement() {
        ProductDTO product = adminClient.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(ProductAttributes.Roles.withValue("myrole")));
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(),
            Pools.random(product).startDate(YEAR_IN_FUTURE).endDate(TWO_YEAR_IN_FUTURE));
        ConsumerDTO consumer = userClient.consumers().createConsumer(
            Consumers.random(owner).type(ConsumerTypes.System.value()).role("myrole"));

        SystemPurposeComplianceStatusDTO status = userClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), YEAR_AND_HALF_IN_FUTURE.toString());
        assertNonComplianceRole(status, "myrole");

        userClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);

        // Check that the status is only matched during the period that the entitlement is valid
        status = userClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertNonComplianceRole(status, "myrole");

        status = userClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), YEAR_AND_HALF_IN_FUTURE.toString());
        assertStatus(status, "matched");
        assertPoolIdComplianceRole(pool, status);

        status = userClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), YEAR_IN_FUTURE.minusDays(1).toString());
        assertNonComplianceRole(status, "myrole");

        status = userClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), TWO_YEAR_IN_FUTURE.plusDays(1).toString());
        assertNonComplianceRole(status, "myrole");
    }

    @Test
    public void shouldNotRecalculateConsumerStatusDuringStatusCallWithSpecifiedDate() {
        ProductDTO product = adminClient.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(ProductAttributes.Roles.withValue("myrole")));
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(),
            Pools.random(product).startDate(YEAR_IN_FUTURE).endDate(TWO_YEAR_IN_FUTURE));
        ConsumerDTO consumer = userClient.consumers().createConsumer(
            Consumers.random(owner).type(ConsumerTypes.System.value()).role("myrole"));
        consumer = userClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer)
            .isNotNull()
            .returns("mismatched", ConsumerDTO::getSystemPurposeStatus);

        // check that the status has not been miscalculated during a bind operation
        userClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);
        consumer = userClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer)
            .isNotNull()
            .returns("mismatched", ConsumerDTO::getSystemPurposeStatus);

        SystemPurposeComplianceStatusDTO status = userClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertNonComplianceRole(status, "myrole");
        consumer = userClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer)
            .isNotNull()
            .returns("mismatched", ConsumerDTO::getSystemPurposeStatus);

        // check that the status has not been recalculated & persisted during a call using on_date
        status = userClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), YEAR_AND_HALF_IN_FUTURE.toString());
        assertStatus(status, "matched");
        assertPoolIdComplianceRole(pool, status);
        consumer = userClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer)
            .isNotNull()
            .returns("mismatched", ConsumerDTO::getSystemPurposeStatus);
    }

    @Test
    public void shouldChangeToMatchedAfterSatisfyingRole() {
        ProductDTO product = adminClient.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(ProductAttributes.Roles.withValue("myrole")));
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = userClient.consumers().createConsumer(
            Consumers.random(owner).type(ConsumerTypes.System.value()).role("myrole"));

        SystemPurposeComplianceStatusDTO status = userClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertNonComplianceRole(status, "myrole");

        userClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);
        status = userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertStatus(status, "matched");
        assertPoolIdComplianceRole(pool, status);
    }

    @Test
    public void shouldBeNotSpecifiedForAnySLAWhenConsumerHasNullSLA() {
        ProductDTO product = adminClient.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(ProductAttributes.SupportLevel.withValue("mysla")));
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = userClient.consumers().createConsumer(
            Consumers.random(owner).type(ConsumerTypes.System.value()));

        userClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);
        SystemPurposeComplianceStatusDTO status = userClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status)
            .isNotNull()
            .returns("not specified", SystemPurposeComplianceStatusDTO::getStatus)
            .extracting(SystemPurposeComplianceStatusDTO::getCompliantSLA)
            .satisfies(stringSetMap -> assertThat(stringSetMap).isEmpty());
    }

    @Test
    public void shouldBeNotSpecifiedForAnyUsageWhenConsumerHasNullUsage() {
        ProductDTO product = adminClient.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(ProductAttributes.Usage.withValue("myusage")));
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = userClient.consumers().createConsumer(
            Consumers.random(owner).type(ConsumerTypes.System.value()));

        userClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);
        SystemPurposeComplianceStatusDTO status = userClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status)
            .isNotNull()
            .returns("not specified", SystemPurposeComplianceStatusDTO::getStatus)
            .extracting(SystemPurposeComplianceStatusDTO::getCompliantUsage)
            .satisfies(stringSetMap -> assertThat(stringSetMap).isEmpty());
    }

    @Test
    public void shouldBeNotSpecifiedForAnyRoleWhenConsumerHasNullRole() {
        ProductDTO product = adminClient.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(ProductAttributes.Roles.withValue("myrole")));
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = userClient.consumers().createConsumer(
            Consumers.random(owner).type(ConsumerTypes.System.value()));

        userClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);
        SystemPurposeComplianceStatusDTO status = userClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status)
            .isNotNull()
            .returns("not specified", SystemPurposeComplianceStatusDTO::getStatus)
            .extracting(SystemPurposeComplianceStatusDTO::getCompliantRole)
            .satisfies(stringSetMap -> assertThat(stringSetMap).isEmpty());
    }

    @Test
    @SuppressWarnings("checkstyle:indentation")
    public void shouldBeNotSpecifiedForAnyAddonsWhenConsumerHasNullAddons() {
        ProductDTO product = adminClient.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(ProductAttributes.Addons.withValue("myaddon"),
                ProductAttributes.Addons.withValue("myotheraddon")));
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = userClient.consumers().createConsumer(
            Consumers.random(owner).type(ConsumerTypes.System.value()));

        userClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);
        SystemPurposeComplianceStatusDTO status = userClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status)
            .isNotNull()
            .returns("not specified", SystemPurposeComplianceStatusDTO::getStatus)
            .extracting(SystemPurposeComplianceStatusDTO::getCompliantAddOns)
            .satisfies(stringSetMap -> assertThat(stringSetMap).isEmpty());
    }

    @Test
    @SuppressWarnings("checkstyle:indentation")
    public void shouldBeMismatchedForUnsatisfiedUsage() {
        ConsumerDTO consumer = userClient.consumers().createConsumer(
            Consumers.random(owner).type(ConsumerTypes.System.value()).usage("taylor"));
        SystemPurposeComplianceStatusDTO status = userClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status)
            .isNotNull()
            .returns("mismatched", SystemPurposeComplianceStatusDTO::getStatus)
            .returns("taylor", SystemPurposeComplianceStatusDTO::getNonCompliantUsage)
            .extracting(SystemPurposeComplianceStatusDTO::getReasons)
            .isEqualTo(Set.of("The requested usage preference \"taylor\" is not provided by a currently" +
                " consumed subscription."));
    }

    @Test
    public void shouldChangeToMatchedAfterSatisfyingUsage() {
        ProductDTO product = adminClient.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(ProductAttributes.Usage.withValue("myusage")));
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = userClient.consumers().createConsumer(
            Consumers.random(owner).type(ConsumerTypes.System.value()).usage("myusage"));

        SystemPurposeComplianceStatusDTO status = userClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status)
            .isNotNull()
            .returns("mismatched", SystemPurposeComplianceStatusDTO::getStatus)
            .returns("myusage", SystemPurposeComplianceStatusDTO::getNonCompliantUsage);

        userClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);
        status = userClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertStatus(status, "matched");
        assertPoolIdComplianceUsage(pool, status);
    }

    @Test
    @SuppressWarnings("checkstyle:indentation")
    public void shouldBeMismatchedForUnsatisfiedAddon() {
        ConsumerDTO consumer = userClient.consumers().createConsumer(
            Consumers.random(owner).addOns(Set.of("addons1", "addons2")));
        SystemPurposeComplianceStatusDTO status = userClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertStatus(status, "mismatched");
        assertThat(status)
            .extracting(SystemPurposeComplianceStatusDTO::getNonCompliantAddOns)
            .isEqualTo(Set.of("addons1", "addons2"));
        assertThat(status.getReasons())
            .isNotNull()
            .hasSize(2)
            .contains(
                "The requested add-on \"addons1\" is not provided by a currently consumed subscription.",
                "The requested add-on \"addons2\" is not provided by a currently consumed subscription.");
    }

    @Test
    public void shouldChangeToMatchedAfterSatisfyingAllAddons() {
        ProductDTO product1 = adminClient.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(ProductAttributes.Addons.withValue("addons1")));
        PoolDTO pool1 = adminClient.owners().createPool(owner.getKey(), Pools.random(product1));
        ProductDTO product2 = adminClient.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(ProductAttributes.Addons.withValue("addons2")));
        PoolDTO pool2 = adminClient.owners().createPool(owner.getKey(), Pools.random(product2));
        ConsumerDTO consumer = userClient.consumers().createConsumer(
            Consumers.random(owner).addOns(Set.of("addons1", "addons2")));

        SystemPurposeComplianceStatusDTO status = userClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertStatus(status, "mismatched");
        assertThat(status)
            .extracting(SystemPurposeComplianceStatusDTO::getNonCompliantAddOns)
            .isEqualTo(Set.of("addons1", "addons2"));

        userClient.consumers().bindPool(consumer.getUuid(), pool1.getId(), 1);
        status = userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertStatus(status, "mismatched");
        assertThat(status)
            .extracting(SystemPurposeComplianceStatusDTO::getNonCompliantAddOns)
            .isEqualTo(Set.of("addons2"));
        assertPoolIdComplianceAddons(pool1, "addons1", status);

        userClient.consumers().bindPool(consumer.getUuid(), pool2.getId(), 1);
        status = userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertStatus(status, "matched");
        assertThat(status)
            .extracting(SystemPurposeComplianceStatusDTO::getNonCompliantAddOns)
            .satisfies(stringSetMap -> assertThat(stringSetMap).isEmpty());
        assertPoolIdComplianceAddons(pool1, "addons1", status);
        assertPoolIdComplianceAddons(pool2, "addons2", status);

    }

    @Test
    @SuppressWarnings("checkstyle:indentation")
    public void shouldBeMismatchedForUnsatisfiedSLA() {
        ProductDTO product1 = adminClient.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(ProductAttributes.SupportLevel.withValue("mysla")));
        adminClient.owners().createPool(owner.getKey(), Pools.random(product1));
        ConsumerDTO consumer = userClient.consumers().createConsumer(
            Consumers.random(owner).serviceLevel("mysla"));
        SystemPurposeComplianceStatusDTO status = userClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status)
            .isNotNull()
            .returns("mysla", SystemPurposeComplianceStatusDTO::getNonCompliantSLA)
            .extracting(SystemPurposeComplianceStatusDTO::getReasons)
            .isNotNull()
            .isEqualTo(Set.of("The service level preference \"mysla\" is not provided by a currently" +
                " consumed subscription."));

        // should not change for another SLA
        ProductDTO product2 = adminClient.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(ProductAttributes.SupportLevel.withValue("anothersla")));
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(product2));
        userClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);
        status = userClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status)
            .isNotNull()
            .returns("mismatched", SystemPurposeComplianceStatusDTO::getStatus)
            .returns("mysla", SystemPurposeComplianceStatusDTO::getNonCompliantSLA)
            .extracting(SystemPurposeComplianceStatusDTO::getReasons)
            .isEqualTo(Set.of("The service level preference \"mysla\" is not provided by" +
                " a currently consumed subscription."));
    }

    @Test
    public void shouldChangeToMatchedAfterSatisfyingSLA() {
        ProductDTO product = adminClient.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(ProductAttributes.SupportLevel.withValue("mysla")));
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = userClient.consumers().createConsumer(
            Consumers.random(owner).serviceLevel("mysla"));
        SystemPurposeComplianceStatusDTO status = userClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status)
            .isNotNull()
            .returns("mismatched", SystemPurposeComplianceStatusDTO::getStatus)
            .returns("mysla", SystemPurposeComplianceStatusDTO::getNonCompliantSLA);

        userClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);
        status = userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertStatus(status, "matched");
        assertPoolIdCompliantSLA(pool, status);
    }

    @Test
    public void shouldBeMatchedForMixedSLAs() {
        ProductDTO product1 = adminClient.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(ProductAttributes.SupportLevel.withValue("mysla")));
        PoolDTO pool1 = adminClient.owners().createPool(owner.getKey(), Pools.random(product1));
        ProductDTO product2 = adminClient.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(ProductAttributes.SupportLevel.withValue("anothersla")));
        PoolDTO pool2 = adminClient.owners().createPool(owner.getKey(), Pools.random(product2));
        ConsumerDTO consumer = userClient.consumers().createConsumer(
            Consumers.random(owner).serviceLevel("mysla"));
        userClient.consumers().bindPool(consumer.getUuid(), pool1.getId(), 1);
        userClient.consumers().bindPool(consumer.getUuid(), pool2.getId(), 1);
        SystemPurposeComplianceStatusDTO status = userClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertStatus(status, "matched");
        assertPoolIdCompliantSLA(pool1, status);
        assertThat(status.getReasons())
            .hasSize(0);
    }

    @Test
    public void shouldBeMatchedForMixedUsages() {
        ProductDTO product1 = adminClient.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(ProductAttributes.Usage.withValue("myusage")));
        PoolDTO pool1 = adminClient.owners().createPool(owner.getKey(), Pools.random(product1));
        ProductDTO product2 = adminClient.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(ProductAttributes.Usage.withValue("anotherusage")));
        PoolDTO pool2 = adminClient.owners().createPool(owner.getKey(), Pools.random(product2));
        ConsumerDTO consumer = userClient.consumers().createConsumer(
            Consumers.random(owner).usage("myusage"));
        userClient.consumers().bindPool(consumer.getUuid(), pool1.getId(), 1);
        userClient.consumers().bindPool(consumer.getUuid(), pool2.getId(), 1);
        SystemPurposeComplianceStatusDTO status = userClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertStatus(status, "matched");
        assertPoolIdComplianceUsage(pool1, status);
        assertThat(status.getReasons())
            .hasSize(0);
    }

    @Test
    public void shouldChangeToMismatchedAfterRevokingPools() {
        ProductDTO product = adminClient.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(ProductAttributes.Usage.withValue("myusage")));
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = userClient.consumers().createConsumer(
            Consumers.random(owner).usage("myusage"));
        userClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);
        SystemPurposeComplianceStatusDTO status = userClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertStatus(status, "matched");
        assertPoolIdComplianceUsage(pool, status);

        userClient.consumers().unbindAll(consumer.getUuid());
        status = userClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status)
            .isNotNull()
            .returns("mismatched", SystemPurposeComplianceStatusDTO::getStatus)
            .returns("myusage", SystemPurposeComplianceStatusDTO::getNonCompliantUsage)
            .extracting(SystemPurposeComplianceStatusDTO::getCompliantUsage)
            .satisfies(stringSetMap -> assertThat(stringSetMap).isEmpty());
    }

    @Test
    @SuppressWarnings("checkstyle:indentation")
    public void shouldAllowSettingSystemPurposePropertiesOnRegistration() {
        ProductDTO product = adminClient.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(ProductAttributes.SupportLevel.withValue("premium")));
        adminClient.owners().createPool(owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = userClient.consumers().createConsumer(
            Consumers.random(owner)
                .usage("u-sage")
                .serviceLevel("premium")
                .role("role")
                .addOns(Set.of("add-on 1", "add-on 2")));

        assertThat(consumer)
            .isNotNull()
            .returns("role", ConsumerDTO::getRole)
            .returns("u-sage", ConsumerDTO::getUsage)
            .returns("premium", ConsumerDTO::getServiceLevel)
            .extracting(ConsumerDTO::getAddOns)
            .isEqualTo(Set.of("add-on 1", "add-on 2"));
    }

    @Test
    @SuppressWarnings("checkstyle:indentation")
    public void shouldAllowUpdatingSystemPurposePropertiesOnConsumer() {
        ProductDTO product1 = adminClient.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(ProductAttributes.SupportLevel.withValue("basic"),
                ProductAttributes.SupportType.withValue("basic_support")));
        ProductDTO product2 = adminClient.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(ProductAttributes.SupportLevel.withValue("premium"),
                ProductAttributes.SupportType.withValue("premium_support")));
        adminClient.owners().createPool(owner.getKey(), Pools.random(product1));
        adminClient.owners().createPool(owner.getKey(), Pools.random(product2));
        ConsumerDTO consumer = userClient.consumers().createConsumer(
            Consumers.random(owner)
                .usage("u-sage")
                .serviceLevel("basic")
                .role("role")
                .addOns(Set.of("add-on 1", "add-on 2"))
                .serviceType("basic_support"));

        assertThat(consumer)
            .isNotNull()
            .returns("basic", ConsumerDTO::getServiceLevel)
            .returns("role", ConsumerDTO::getRole)
            .returns("u-sage", ConsumerDTO::getUsage)
            .returns("basic_support", ConsumerDTO::getServiceType)
            .extracting(ConsumerDTO::getAddOns)
            .isEqualTo(Set.of("add-on 1", "add-on 2"));

        ConsumerDTO updateToConsumer = consumer
            .serviceLevel("premium")
            .role("updatedrole")
            .usage("updatedusage")
            .addOns(Set.of("add-on 2", "add-on 4"))
            .serviceType("updatedsupport");


        adminClient.consumers().updateConsumer(consumer.getUuid(), updateToConsumer);
        ConsumerDTO updatedConsumer = adminClient.consumers().getConsumer(consumer.getUuid());
        assertThat(updatedConsumer)
            .isNotNull()
            .returns("premium", ConsumerDTO::getServiceLevel)
            .returns("updatedrole", ConsumerDTO::getRole)
            .returns("updatedusage", ConsumerDTO::getUsage)
            .returns("updatedsupport", ConsumerDTO::getServiceType)
            .extracting(ConsumerDTO::getAddOns)
            .isEqualTo(Set.of("add-on 4", "add-on 2"));
    }

    @Test
    @SuppressWarnings("checkstyle:indentation")
    public void shouldAllowClearingSystemPurposePropertiesOnConsumer() {
        // Create a product and pool so the service level is available.
        ProductDTO product = adminClient.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(ProductAttributes.SupportLevel.withValue("basic")));
        adminClient.owners().createPool(owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = userClient.consumers().createConsumer(
            Consumers.random(owner)
                .usage("u-sage")
                .serviceLevel("basic")
                .role("role")
                .addOns(Set.of("add-on 1", "add-on 2"))
                .serviceType("basic_support"));

        assertThat(consumer)
            .isNotNull()
            .returns("basic", ConsumerDTO::getServiceLevel)
            .returns("role", ConsumerDTO::getRole)
            .returns("u-sage", ConsumerDTO::getUsage)
            .returns("basic_support", ConsumerDTO::getServiceType)
            .extracting(ConsumerDTO::getAddOns)
            .isEqualTo(Set.of("add-on 1", "add-on 2"));

        ConsumerDTO updateToConsumer = consumer
            .serviceLevel("")
            .role("")
            .usage("")
            .addOns(Set.of())
            .serviceType("");

        adminClient.consumers().updateConsumer(consumer.getUuid(), updateToConsumer);
        ConsumerDTO updatedConsumer = adminClient.consumers().getConsumer(consumer.getUuid());
        assertThat(updatedConsumer)
            .isNotNull()
            .returns("", ConsumerDTO::getServiceLevel)
            .returns("", ConsumerDTO::getRole)
            .returns("", ConsumerDTO::getUsage)
            .returns("", ConsumerDTO::getServiceType)
            .extracting(ConsumerDTO::getAddOns)
            .isEqualTo(Set.of());
    }

    @Test
    public void shouldBeNotSpecifiedForAnyServiceTypeWhenConsumerHasNullServiceType() {
        ProductDTO product = adminClient.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(ProductAttributes.SupportType.withValue("test_support")));
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));

        userClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);
        SystemPurposeComplianceStatusDTO status = userClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), null);

        assertThat(status)
            .isNotNull()
            .returns("not specified", SystemPurposeComplianceStatusDTO::getStatus)
            .extracting(SystemPurposeComplianceStatusDTO::getCompliantServiceType)
            .satisfies(serviceType -> assertThat(serviceType).isEmpty());
    }

    @Test
    @SuppressWarnings("checkstyle:indentation")
    public void shouldBeMismatchedForUnsatisfiedServiceType() {
        ConsumerDTO consumer = userClient.consumers().createConsumer(
            Consumers.random(owner).serviceType("test_service_type"));
        SystemPurposeComplianceStatusDTO status = userClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), null);

        assertThat(status)
            .isNotNull()
            .returns("mismatched", SystemPurposeComplianceStatusDTO::getStatus)
            .returns("test_service_type", SystemPurposeComplianceStatusDTO::getNonCompliantServiceType)
            .extracting(SystemPurposeComplianceStatusDTO::getReasons)
            .isEqualTo(Set.of("The requested service type preference \"test_service_type\"" +
                " is not provided by a currently consumed subscription."));
    }

    @Test
    public void shouldChangeToMatchedAfterSatisfyingServiceType() {
        ProductDTO product = adminClient.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(ProductAttributes.SupportType.withValue("test_service_type")));
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = userClient.consumers().createConsumer(
            Consumers.random(owner).serviceType("test_service_type"));

        SystemPurposeComplianceStatusDTO status = userClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status)
            .isNotNull()
            .returns("mismatched", SystemPurposeComplianceStatusDTO::getStatus)
            .returns("test_service_type", SystemPurposeComplianceStatusDTO::getNonCompliantServiceType);

        userClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);
        status = userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertStatus(status, "matched");
        assertPoolIdCompliantServiceType(pool, status, "test_service_type");
    }

    @Test
    public void shouldBeMatchedForMixedServiceType() {
        ProductDTO product1 = adminClient.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(ProductAttributes.SupportType.withValue("L1")));
        PoolDTO pool1 = adminClient.owners().createPool(owner.getKey(), Pools.random(product1));
        ProductDTO product2 = adminClient.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(ProductAttributes.SupportType.withValue("L1")));
        PoolDTO pool2 = adminClient.owners().createPool(owner.getKey(), Pools.random(product2));
        ConsumerDTO consumer = userClient.consumers().createConsumer(
            Consumers.random(owner).serviceType("L1"));

        userClient.consumers().bindPool(consumer.getUuid(), pool1.getId(), 1);
        userClient.consumers().bindPool(consumer.getUuid(), pool2.getId(), 1);
        SystemPurposeComplianceStatusDTO status = userClient.consumers()
            .getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertStatus(status, "matched");
        assertPoolIdCompliantServiceType(pool1, status, "L1");
        assertThat(status.getReasons())
            .isNotNull()
            .hasSize(0);
    }

    private static void assertPoolIdComplianceRole(PoolDTO pool, SystemPurposeComplianceStatusDTO status) {
        assertThat(status.getCompliantRole().get("myrole"))
            .isNotNull()
            .map(EntitlementDTO::getPool)
            .isNotNull()
            .map(PoolDTO::getId)
            .isNotNull()
            .anyMatch(pool.getId()::equalsIgnoreCase);
    }

    private static void assertPoolIdComplianceUsage(PoolDTO pool, SystemPurposeComplianceStatusDTO status) {
        assertThat(status.getCompliantUsage().get("myusage"))
            .isNotNull()
            .map(EntitlementDTO::getPool)
            .isNotNull()
            .map(PoolDTO::getId)
            .isNotNull()
            .anyMatch(pool.getId()::equalsIgnoreCase);
    }

    private static void assertPoolIdComplianceAddons(PoolDTO pool, String addon,
        SystemPurposeComplianceStatusDTO status) {
        assertThat(status.getCompliantAddOns().get(addon))
            .isNotNull()
            .map(EntitlementDTO::getPool)
            .isNotNull()
            .map(PoolDTO::getId)
            .isNotNull()
            .anyMatch(pool.getId()::equalsIgnoreCase);
    }

    private static void assertPoolIdCompliantSLA(PoolDTO pool, SystemPurposeComplianceStatusDTO status) {
        assertThat(status.getCompliantSLA().get("mysla"))
            .isNotNull()
            .map(EntitlementDTO::getPool)
            .isNotNull()
            .map(PoolDTO::getId)
            .isNotNull()
            .anyMatch(pool.getId()::equalsIgnoreCase);
    }

    private static void assertPoolIdCompliantServiceType(
        PoolDTO pool, SystemPurposeComplianceStatusDTO status, String serviceName) {
        assertThat(status.getCompliantServiceType().get(serviceName))
            .isNotNull()
            .map(EntitlementDTO::getPool)
            .isNotNull()
            .map(PoolDTO::getId)
            .isNotNull()
            .anyMatch(pool.getId()::equalsIgnoreCase);
    }

    private static void assertStatus(SystemPurposeComplianceStatusDTO statusDTO, String status) {
        assertThat(statusDTO)
            .isNotNull()
            .hasFieldOrPropertyWithValue("status", status);
    }


    private static void assertNonComplianceRole(SystemPurposeComplianceStatusDTO status, String role) {
        assertThat(status)
            .isNotNull()
            .hasFieldOrPropertyWithValue("status", "mismatched")
            .hasFieldOrPropertyWithValue("nonCompliantRole", role);
    }
}
