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
package org.candlepin.spec.entitlements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;

import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerInstalledProductDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.GuestIdDTO;
import org.candlepin.dto.api.client.v1.HypervisorIdDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.SystemPurposeComplianceStatusDTO;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;


@SpecTest
public class AutobindSpecTest {

    private static ApiClient client;
    private static OwnerClient ownerApi;
    private static OwnerProductApi ownerProductApi;

    @BeforeAll
    public static void beforeAll() {
        client = ApiClients.admin();
        ownerApi = client.owners();
        ownerProductApi = client.ownerProducts();
    }

    @Test
    public void shouldSucceedWhenRequestingBindOfMultiplePoolsWithSameStackId() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        //  create 4 products with the same stack id and sockets.
        ProductDTO product = Products.randomEng()
            .addAttributesItem(ProductAttributes.StackingId.withValue("ouch"))
            .addAttributesItem(ProductAttributes.Sockets.withValue("2"))
            .addAttributesItem(ProductAttributes.VirtualCpu.withValue("4"))
            .addAttributesItem(ProductAttributes.WarningPeriod.withValue("30"))
            .addAttributesItem(ProductAttributes.BrandType.withValue("OS"));
        ownerProductApi.createProduct(owner.getKey(), product);

        List<ProductDTO> skus = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            ProductDTO sku = Products.random()
                .addAttributesItem(ProductAttributes.StackingId.withValue("ouch"))
                .addAttributesItem(ProductAttributes.VirtualLimit.withValue("1"))
                .addAttributesItem(ProductAttributes.Sockets.withValue("1"))
                .addAttributesItem(ProductAttributes.InstanceMultiplier.withValue("1"))
                .addAttributesItem(ProductAttributes.MultiEntitlement.withValue("yes"))
                .addAttributesItem(ProductAttributes.HostLimited.withValue("true"))
                .providedProducts(Set.of(product));
            skus.add(ownerProductApi.createProduct(owner.getKey(), sku));
        }

        // create 4 pools, all must provide product "prod" . none of them
        // should provide enough sockets to heal the host on its own
        ownerApi.createPool(owner.getKey(), Pools.random(product).quantity(10L));
        ownerApi.createPool(owner.getKey(), Pools.random(skus.get(0)).quantity(30L));
        ownerApi.createPool(owner.getKey(), Pools.random(skus.get(1)).quantity(30L));
        ownerApi.createPool(owner.getKey(), Pools.random(skus.get(2)).quantity(30L));

        // create a guest with "prod" as an installed product
        String guestUuid =  StringUtil.random("uuid");
        ConsumerInstalledProductDTO installed = new ConsumerInstalledProductDTO()
            .productId(product.getId())
            .productName(product.getName());
        ConsumerDTO guest = Consumers.random(owner)
            .facts(Map.of("virt.is_guest", "true", "virt.uuid", guestUuid,
            "cpu.cpu_socket(s)", "1", "virt.host_type", "kvm",
            "system.certificate_version", "3.2"))
            .installedProducts(Set.of(installed));
        guest = userClient.consumers().createConsumer(guest);
        ApiClient guestClient = ApiClients.ssl(guest);

        // create a hypervisor that needs 40 sockets and report the guest with it
        String hypervisorUuid = StringUtil.random("uuid");
        ConsumerDTO host = Consumers.random(owner)
            .hypervisorId(new HypervisorIdDTO().hypervisorId(hypervisorUuid))
            .facts(Map.of("virt.is_guest", "false", "cpu.cpu(s)", "4",
            "cpu.cpu_socket(s)", "40"));
        host = userClient.consumers().createConsumer(host);
        ApiClient hostClient = ApiClients.ssl(host);
        hostClient.consumers().updateConsumer(host.getUuid(),
            new ConsumerDTO().guestIds(List.of(new GuestIdDTO().guestId(guestUuid))));

        assertThat(ownerApi.listOwnerPools(owner.getKey())).hasSize(4);
        guestClient.consumers().autoBind(guest.getUuid());
        assertThat(ownerApi.listOwnerPools(owner.getKey())).hasSize(5);

        // heal should succeed, and hypervisor should consume 2 pools of 30 sockets each
        assertThat(hostClient.consumers().listEntitlements(host.getUuid())).hasSize(2);
        assertThat(guestClient.consumers().listEntitlements(guest.getUuid())).hasSize(1);

        hostClient.consumers().unbindAll(host.getUuid());
        guestClient.consumers().unbindAll(guest.getUuid());

        // change the hypervisor to 70 sockets
        hostClient.consumers().updateConsumer(host.getUuid(), new ConsumerDTO()
            .facts(Map.of("virt.is_guest", "false", "cpu.cpu(s)", "4",
            "cpu.cpu_socket(s)", "70")));

        // heal should succeed, and hypervisor should consume 3 pools of 30 sockets each
        guestClient.consumers().autoBind(guest.getUuid());
        assertThat(hostClient.consumers().listEntitlements(host.getUuid())).hasSize(3);
        assertThat(guestClient.consumers().listEntitlements(guest.getUuid())).hasSize(1);
        assertThat(ownerApi.listOwnerPools(owner.getKey())).hasSize(5);
    }

    @Test
    public void shouldAttachToAddonPoolWhenProductIsNotInstalled() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO product1 = Products.randomEng()
            .addAttributesItem(ProductAttributes.Addons.withValue("addon1"));
        product1 = ownerProductApi.createProduct(owner.getKey(), product1);
        PoolDTO pool1 = ownerApi.createPool(owner.getKey(), Pools.random(product1));
        ProductDTO product2 = ownerProductApi.createProduct(owner.getKey(), Products.randomEng());
        PoolDTO pool2 = ownerApi.createPool(owner.getKey(), Pools.random(product2));

        ConsumerInstalledProductDTO installed = new ConsumerInstalledProductDTO()
            .productId(product2.getId())
            .productName(product2.getName());
        ConsumerDTO consumer = Consumers.random(owner)
            .addAddOnsItem("addon1")
            .installedProducts(Set.of(installed));
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        SystemPurposeComplianceStatusDTO status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("mismatched");
        assertThat(status.getNonCompliantAddOns().stream().findFirst().get()).isEqualTo("addon1");

        assertThat(consumerClient.consumers().autoBind(consumer.getUuid())).hasSize(2);
        status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("matched");
        assertThat(status.getNonCompliantAddOns()).hasSize(0);
        assertThat(status.getCompliantAddOns().get("addon1"))
            .singleElement()
            .returns(pool1.getId(), x -> x.getPool().getId());
    }

    @Test
    public void shouldAsyncAttachToAddonPoolWhenProductIsNotInstalled() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO product1 = Products.randomEng()
            .addAttributesItem(ProductAttributes.Addons.withValue("addon1"));
        product1 = ownerProductApi.createProduct(owner.getKey(), product1);
        PoolDTO pool1 = ownerApi.createPool(owner.getKey(), Pools.random(product1));
        ProductDTO product2 = ownerProductApi.createProduct(owner.getKey(), Products.randomEng());
        PoolDTO pool2 = ownerApi.createPool(owner.getKey(), Pools.random(product2));

        ConsumerInstalledProductDTO installed = new ConsumerInstalledProductDTO()
            .productId(product2.getId())
            .productName(product2.getName());
        ConsumerDTO consumer = Consumers.random(owner)
            .addAddOnsItem("addon1")
            .installedProducts(Set.of(installed));
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        assertThat(consumerClient.consumers().listEntitlements(consumer.getUuid())).isEmpty();
        JsonNode job = getJsonNode(consumerClient.consumers().bind(consumer.getUuid(), null, null, null,
            "", "", true, null, new ArrayList<>()));
        assertThatJob(client.jobs().waitForJob(job.get("id").asText())).isFinished();
        assertThat(consumerClient.consumers().listEntitlements(consumer.getUuid())).hasSize(2);
    }

    @Test
    public void shouldAttachToRolePoolWhenProductIsNotInstalled() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO product1 = Products.randomEng()
            .addAttributesItem(ProductAttributes.Roles.withValue("role1"));
        product1 = ownerProductApi.createProduct(owner.getKey(), product1);
        PoolDTO pool1 = ownerApi.createPool(owner.getKey(), Pools.random(product1));
        ProductDTO product2 = ownerProductApi.createProduct(owner.getKey(), Products.randomEng());
        PoolDTO pool2 = ownerApi.createPool(owner.getKey(), Pools.random(product2));

        ConsumerInstalledProductDTO installed = new ConsumerInstalledProductDTO()
            .productId(product2.getId())
            .productName(product2.getName());
        ConsumerDTO consumer = Consumers.random(owner)
            .role("role1")
            .installedProducts(Set.of(installed));
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        SystemPurposeComplianceStatusDTO status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("mismatched");
        assertThat(status.getNonCompliantRole()).isEqualTo("role1");

        assertThat(consumerClient.consumers().autoBind(consumer.getUuid())).hasSize(2);
        status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("matched");
        assertThat(status.getNonCompliantRole()).isNull();
        assertThat(status.getCompliantRole().get("role1"))
            .singleElement()
            .returns(pool1.getId(), x -> x.getPool().getId());
    }

    @Test
    public void shouldPoolWithRoleHavePriorityOverPoolWithout() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO engProduct = ownerProductApi.createProduct(owner.getKey(), Products.randomEng());
        ProductDTO mktProduct1 = Products.random()
            .addAttributesItem(ProductAttributes.Roles.withValue("role1"))
            .providedProducts(Set.of(engProduct));
        mktProduct1 = ownerProductApi.createProduct(owner.getKey(), mktProduct1);
        ProductDTO mktProduct2 = Products.random()
            .providedProducts(Set.of(engProduct));
        mktProduct2 = ownerProductApi.createProduct(owner.getKey(), mktProduct2);
        PoolDTO pool1 = ownerApi.createPool(owner.getKey(), Pools.random(mktProduct1));
        PoolDTO pool2 = ownerApi.createPool(owner.getKey(), Pools.random(mktProduct2));

        ConsumerInstalledProductDTO installed = new ConsumerInstalledProductDTO()
            .productId(engProduct.getId())
            .productName(engProduct.getName());
        ConsumerDTO consumer = Consumers.random(owner)
            .role("role1")
            .installedProducts(Set.of(installed));
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        SystemPurposeComplianceStatusDTO status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("mismatched");
        assertThat(status.getNonCompliantRole()).isEqualTo("role1");

        assertThat(consumerClient.consumers().autoBind(consumer.getUuid())).hasSize(1);
        status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("matched");
        assertThat(status.getNonCompliantRole()).isNull();
        assertThat(status.getCompliantRole().get("role1"))
            .singleElement()
            .returns(pool1.getId(), x -> x.getPool().getId());
    }

    @Test
    public void shouldPoolWithAddonHavePriorityOverPoolWithout() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO engProduct = ownerProductApi.createProduct(owner.getKey(), Products.randomEng());
        ProductDTO mktProduct1 = Products.random()
            .addAttributesItem(ProductAttributes.Addons.withValue("addon1"))
            .providedProducts(Set.of(engProduct));
        mktProduct1 = ownerProductApi.createProduct(owner.getKey(), mktProduct1);
        ProductDTO mktProduct2 = Products.random()
            .providedProducts(Set.of(engProduct));
        mktProduct2 = ownerProductApi.createProduct(owner.getKey(), mktProduct2);
        PoolDTO pool1 = ownerApi.createPool(owner.getKey(), Pools.random(mktProduct1));
        PoolDTO pool2 = ownerApi.createPool(owner.getKey(), Pools.random(mktProduct2));

        ConsumerInstalledProductDTO installed = new ConsumerInstalledProductDTO()
            .productId(engProduct.getId())
            .productName(engProduct.getName());
        ConsumerDTO consumer = Consumers.random(owner)
            .addAddOnsItem("addon1")
            .installedProducts(Set.of(installed));
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        SystemPurposeComplianceStatusDTO status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("mismatched");
        assertThat(status.getNonCompliantAddOns().stream().findFirst().get()).isEqualTo("addon1");

        assertThat(consumerClient.consumers().autoBind(consumer.getUuid())).hasSize(1);
        status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("matched");
        assertThat(status.getNonCompliantAddOns()).hasSize(0);
        assertThat(status.getCompliantAddOns().get("addon1"))
            .singleElement()
            .returns(pool1.getId(), x -> x.getPool().getId());
    }

    @Test
    public void shouldAttachMoreThanOneAddonPoolWhenNeeded() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO product1 = Products.randomEng()
            .addAttributesItem(ProductAttributes.Addons.withValue("addon1"));
        product1 = ownerProductApi.createProduct(owner.getKey(), product1);
        PoolDTO pool1 = ownerApi.createPool(owner.getKey(), Pools.random(product1));
        ProductDTO product2 = Products.randomEng()
            .addAttributesItem(ProductAttributes.Addons.withValue("addon2"));
        product2 = ownerProductApi.createProduct(owner.getKey(), product2);
        PoolDTO pool2 = ownerApi.createPool(owner.getKey(), Pools.random(product2));

        ConsumerInstalledProductDTO installed = new ConsumerInstalledProductDTO()
            .productId(product2.getId())
            .productName(product2.getName());
        ConsumerDTO consumer = Consumers.random(owner)
            .addAddOnsItem("addon1")
            .addAddOnsItem("addon2")
            .installedProducts(Set.of(installed));
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        SystemPurposeComplianceStatusDTO status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("mismatched");
        assertThat(status.getNonCompliantAddOns().stream())
            .contains("addon1")
            .contains("addon2");

        assertThat(consumerClient.consumers().autoBind(consumer.getUuid())).hasSize(2);
        status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("matched");
        assertThat(status.getNonCompliantAddOns()).hasSize(0);
        assertThat(status.getCompliantAddOns().get("addon1"))
            .singleElement()
            .returns(pool1.getId(), x -> x.getPool().getId());
        assertThat(status.getCompliantAddOns().get("addon2"))
            .singleElement()
            .returns(pool2.getId(), x -> x.getPool().getId());
    }

    @Test
    public void shouldPoolWithUsageHavePriorityOverPoolWithout() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO engProduct = ownerProductApi.createProduct(owner.getKey(), Products.randomEng());
        ProductDTO mktProduct1 = Products.random()
            .addAttributesItem(ProductAttributes.Usage.withValue("my_usage"))
            .providedProducts(Set.of(engProduct));
        mktProduct1 = ownerProductApi.createProduct(owner.getKey(), mktProduct1);
        ProductDTO mktProduct2 = Products.random()
            .providedProducts(Set.of(engProduct));
        mktProduct2 = ownerProductApi.createProduct(owner.getKey(), mktProduct2);
        PoolDTO pool1 = ownerApi.createPool(owner.getKey(), Pools.random(mktProduct1));
        PoolDTO pool2 = ownerApi.createPool(owner.getKey(), Pools.random(mktProduct2));

        ConsumerInstalledProductDTO installed = new ConsumerInstalledProductDTO()
            .productId(engProduct.getId())
            .productName(engProduct.getName());
        ConsumerDTO consumer = Consumers.random(owner)
            .usage("my_usage")
            .installedProducts(Set.of(installed));
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        SystemPurposeComplianceStatusDTO status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("mismatched");
        assertThat(status.getNonCompliantUsage()).isEqualTo("my_usage");

        assertThat(consumerClient.consumers().autoBind(consumer.getUuid())).hasSize(1);
        status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("matched");
        assertThat(status.getNonCompliantUsage()).isNull();
        assertThat(status.getCompliantUsage().get("my_usage"))
            .singleElement()
            .returns(pool1.getId(), x -> x.getPool().getId());
    }

    @Test
    public void shouldPoolWithMatchingRoleBeAttachedEvenIfItCoversNoProducts() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO engProduct = ownerProductApi.createProduct(owner.getKey(), Products.randomEng());
        ProductDTO mktProduct1 = Products.random()
            .addAttributesItem(ProductAttributes.Roles.withValue("provided_role,another_role"));
        mktProduct1 = ownerProductApi.createProduct(owner.getKey(), mktProduct1);
        PoolDTO pool1 = ownerApi.createPool(owner.getKey(), Pools.random(mktProduct1));

        ConsumerInstalledProductDTO installed = new ConsumerInstalledProductDTO()
            .productId(engProduct.getId())
            .productName(engProduct.getName());
        ConsumerDTO consumer = Consumers.random(owner)
            .role("provided_role")
            .installedProducts(Set.of(installed));
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        SystemPurposeComplianceStatusDTO status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("mismatched");
        assertThat(status.getNonCompliantRole()).isEqualTo("provided_role");

        assertThat(consumerClient.consumers().autoBind(consumer.getUuid()))
            .singleElement()
            .returns(pool1.getId(), x -> x.get("pool").get("id").asText());

        status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("matched");
        assertThat(status.getNonCompliantRole()).isNull();
        assertThat(status.getCompliantRole().get("provided_role"))
            .singleElement()
            .returns(pool1.getId(), x -> x.getPool().getId());
    }

    @Test
    public void shouldPoolWithMatchingAddonBeAttachedEvenIfItCoversNoProducts() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO engProduct = ownerProductApi.createProduct(owner.getKey(), Products.randomEng());
        ProductDTO mktProduct1 = Products.random()
            .addAttributesItem(ProductAttributes.Addons.withValue("provided_addon,another_addon"))
            .providedProducts(Set.of(engProduct));
        mktProduct1 = ownerProductApi.createProduct(owner.getKey(), mktProduct1);
        PoolDTO pool1 = ownerApi.createPool(owner.getKey(), Pools.random(mktProduct1));

        ConsumerInstalledProductDTO installed = new ConsumerInstalledProductDTO()
            .productId(engProduct.getId())
            .productName(engProduct.getName());
        ConsumerDTO consumer = Consumers.random(owner)
            .addAddOnsItem("provided_addon")
            .addAddOnsItem("random_addon")
            .installedProducts(Set.of(installed));
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        SystemPurposeComplianceStatusDTO status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("mismatched");
        assertThat(status.getNonCompliantAddOns())
            .contains("provided_addon")
            .contains("random_addon");

        assertThat(consumerClient.consumers().autoBind(consumer.getUuid()))
            .singleElement()
            .returns(pool1.getId(), x -> x.get("pool").get("id").asText());

        status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("mismatched");
        assertThat(status.getNonCompliantAddOns()).contains("random_addon");
        assertThat(status.getCompliantAddOns().get("provided_addon"))
            .singleElement()
            .returns(pool1.getId(), x -> x.getPool().getId());
    }

    @Test
    public void shouldPoolWithMatchingUsageNotBeAttachedWhenItCoversNoProducts() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO engProduct = ownerProductApi.createProduct(owner.getKey(), Products.randomEng());
        ProductDTO mktProduct1 = Products.random()
            .addAttributesItem(ProductAttributes.Usage.withValue("provided_usage"));
        mktProduct1 = ownerProductApi.createProduct(owner.getKey(), mktProduct1);
        PoolDTO pool1 = ownerApi.createPool(owner.getKey(), Pools.random(mktProduct1));

        ConsumerInstalledProductDTO installed = new ConsumerInstalledProductDTO()
            .productId(engProduct.getId())
            .productName(engProduct.getName());
        ConsumerDTO consumer = Consumers.random(owner)
            .usage("provided_usage")
            .installedProducts(Set.of(installed));
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        SystemPurposeComplianceStatusDTO status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("mismatched");
        assertThat(status.getNonCompliantUsage()).isEqualTo("provided_usage");

        assertThat(consumerClient.consumers().autoBind(consumer.getUuid())).isEmpty();

        status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("mismatched");
        assertThat(status.getNonCompliantUsage()).isEqualTo("provided_usage");
    }

    @Test
    public void shouldPoolWithMatchingSlaNotBeAttachedWhenItCoversNoProducts() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO engProduct = ownerProductApi.createProduct(owner.getKey(), Products.randomEng());
        ProductDTO mktProduct1 = Products.random()
            .addAttributesItem(ProductAttributes.SupportLevel.withValue("provided_sla"));
        mktProduct1 = ownerProductApi.createProduct(owner.getKey(), mktProduct1);
        PoolDTO pool1 = ownerApi.createPool(owner.getKey(), Pools.random(mktProduct1));

        ConsumerInstalledProductDTO installed = new ConsumerInstalledProductDTO()
            .productId(engProduct.getId())
            .productName(engProduct.getName());
        ConsumerDTO consumer = Consumers.random(owner)
            .serviceLevel("provided_sla")
            .installedProducts(Set.of(installed));
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        SystemPurposeComplianceStatusDTO status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("mismatched");
        assertThat(status.getNonCompliantSLA()).isEqualTo("provided_sla");

        assertThat(consumerClient.consumers().autoBind(consumer.getUuid())).isEmpty();

        status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("mismatched");
        assertThat(status.getNonCompliantSLA()).isEqualTo("provided_sla");
    }

    @Test
    public void shouldAllPoolsWithRoleAddonOrInstalledProductBeAttachedButNotIfTheyMatchUsageOrSlaOnly() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));

        ProductDTO engProduct = ownerProductApi.createProduct(owner.getKey(), Products.randomEng());

        ProductDTO product1 = Products.random().providedProducts(Set.of(engProduct));
        product1 = ownerProductApi.createProduct(owner.getKey(), product1);
        PoolDTO poolWithEngProductOnly = ownerApi.createPool(owner.getKey(), Pools.random(product1));

        ProductDTO product2 = Products.random()
            .addAttributesItem(ProductAttributes.Roles.withValue("provided_role,non_provided_role"));
        product2 = ownerProductApi.createProduct(owner.getKey(), product2);
        PoolDTO poolWithRoleOnly = ownerApi.createPool(owner.getKey(), Pools.random(product2));

        ProductDTO product3 = Products.random()
            .addAttributesItem(ProductAttributes.Addons.withValue("provided_addon,non_provided_addon"));
        product3 = ownerProductApi.createProduct(owner.getKey(), product3);
        PoolDTO poolWithAddonOnly =
            ownerApi.createPool(owner.getKey(), Pools.random(product3));

        ProductDTO product4 = Products.random()
            .addAttributesItem(ProductAttributes.Usage.withValue("provided_usage"));
        product4 = ownerProductApi.createProduct(owner.getKey(), product4);
        PoolDTO poolWithUsageOnly = ownerApi.createPool(owner.getKey(), Pools.random(product4));

        ProductDTO product5 = Products.random()
            .addAttributesItem(ProductAttributes.SupportLevel.withValue("provided_sla"));
        product5 = ownerProductApi.createProduct(owner.getKey(), product5);
        PoolDTO poolWithSlaOnly = ownerApi.createPool(owner.getKey(), Pools.random(product5));

        ConsumerInstalledProductDTO installed = new ConsumerInstalledProductDTO()
            .productId(engProduct.getId())
            .productName(engProduct.getName());
        ConsumerDTO consumer = Consumers.random(owner)
            .serviceLevel("provided_sla")
            .usage("provided_usage")
            .role("provided_role")
            .addAddOnsItem("provided_addon")
            .addAddOnsItem("another_addon")
            .installedProducts(Set.of(installed));
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        SystemPurposeComplianceStatusDTO status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("mismatched");
        assertThat(status.getNonCompliantSLA()).isEqualTo("provided_sla");
        assertThat(status.getNonCompliantUsage()).isEqualTo("provided_usage");
        assertThat(status.getNonCompliantRole()).isEqualTo("provided_role");
        assertThat(status.getNonCompliantAddOns()).contains("provided_addon");
        assertThat(status.getNonCompliantAddOns()).contains("another_addon");

        // Check which pools are attached:
        // The pools attached should be: pool_with_addon_only, pool_with_role_only and
        //  pool_with_eng_product_only
        // The pools NOT attached should be: pool_with_usage_only and pool_with_sla_only
        assertThat(consumerClient.consumers().autoBind(consumer.getUuid()))
            .hasSize(3)
            .map(x -> x.get("pool").get("id").asText())
            .hasSize(3)
            .contains(poolWithAddonOnly.getId())
            .contains(poolWithRoleOnly.getId())
            .contains(poolWithEngProductOnly.getId());

        status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("mismatched");
        assertThat(status.getNonCompliantSLA()).isEqualTo("provided_sla");
        assertThat(status.getNonCompliantUsage()).isEqualTo("provided_usage");
        assertThat(status.getNonCompliantAddOns()).contains("another_addon");

        assertThat(status.getCompliantRole()).containsKey("provided_role");
        assertThat(status.getCompliantAddOns()).containsKey("provided_addon");
    }

    @Test
    public void shouldSlaMatchHaveAHigherImpactThanSocketMismatchOrOtherSyspurposeMismatchesOnAPool() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO engProduct = ownerProductApi.createProduct(owner.getKey(), Products.randomEng());
        ProductDTO mktProduct1 = Products.random()
            .addAttributesItem(ProductAttributes.SupportLevel.withValue("mysla"))
            .addAttributesItem(ProductAttributes.Sockets.withValue("2"))
            .addAttributesItem(ProductAttributes.Roles.withValue("random_role"))
            .addAttributesItem(ProductAttributes.Usage.withValue("random_usage"))
            .addAttributesItem(ProductAttributes.Addons.withValue("one,two"))
            .providedProducts(Set.of(engProduct));
        mktProduct1 = ownerProductApi.createProduct(owner.getKey(), mktProduct1);
        ProductDTO mktProduct2 = Products.random()
            .providedProducts(Set.of(engProduct));
        mktProduct2 = ownerProductApi.createProduct(owner.getKey(), mktProduct2);
        PoolDTO pool1 = ownerApi.createPool(owner.getKey(), Pools.random(mktProduct1));
        PoolDTO pool2 = ownerApi.createPool(owner.getKey(), Pools.random(mktProduct2));

        ConsumerInstalledProductDTO installed = new ConsumerInstalledProductDTO()
            .productId(engProduct.getId())
            .productName(engProduct.getName());
        ConsumerDTO consumer = Consumers.random(owner)
            .facts(Map.of("cpu.cpu_socket(s)", "1"))
            .serviceLevel("mysla")
            .installedProducts(Set.of(installed));
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        consumerClient.consumers().autoBind(consumer.getUuid());
        assertThat(consumerClient.consumers().listEntitlements(consumer.getUuid()))
            .singleElement()
            .returns(pool1.getId(), x -> x.getPool().getId());
    }

    @Test
    public void shouldPoolWithVirtOnlyMatchNotOverpowerPoolWithUsageMatch() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO engProduct = ownerProductApi.createProduct(owner.getKey(), Products.randomEng());
        ProductDTO mktProduct1 = Products.random()
            .addAttributesItem(ProductAttributes.Usage.withValue("my_usage"))
            .providedProducts(Set.of(engProduct));
        mktProduct1 = ownerProductApi.createProduct(owner.getKey(), mktProduct1);
        ProductDTO mktProduct2 = Products.random()
            .addAttributesItem(ProductAttributes.VirtualOnly.withValue("true"))
            .providedProducts(Set.of(engProduct));
        mktProduct2 = ownerProductApi.createProduct(owner.getKey(), mktProduct2);
        PoolDTO pool1 = ownerApi.createPool(owner.getKey(), Pools.random(mktProduct1));
        PoolDTO pool2 = ownerApi.createPool(owner.getKey(), Pools.random(mktProduct2));

        ConsumerInstalledProductDTO installed = new ConsumerInstalledProductDTO()
            .productId(engProduct.getId())
            .productName(engProduct.getName());
        ConsumerDTO consumer = Consumers.random(owner)
            .facts(Map.of("virt.is_guest", "true"))
            .usage("my_usage")
            .installedProducts(Set.of(installed));
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        consumerClient.consumers().autoBind(consumer.getUuid());
        assertThat(consumerClient.consumers().listEntitlements(consumer.getUuid()))
            .singleElement()
            .returns(pool1.getId(), x -> x.getPool().getId());
    }

    @Test
    public void shouldPoolWithVirtOnlyMatchNotOverpowerPhyicalPoolsWithoutSyspurposeMatches() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO engProduct = ownerProductApi.createProduct(owner.getKey(), Products.randomEng());
        ProductDTO mktProduct1 = Products.random()
            .providedProducts(Set.of(engProduct));
        mktProduct1 = ownerProductApi.createProduct(owner.getKey(), mktProduct1);
        ProductDTO mktProduct2 = Products.random()
            .addAttributesItem(ProductAttributes.VirtualOnly.withValue("true"))
            .addAttributesItem(ProductAttributes.Roles.withValue("random_role"))
            .addAttributesItem(ProductAttributes.SupportLevel.withValue("random_sla"))
            .addAttributesItem(ProductAttributes.Usage.withValue("random_usage"))
            .addAttributesItem(ProductAttributes.SupportType.withValue("test_support"))
            .providedProducts(Set.of(engProduct));
        mktProduct2 = ownerProductApi.createProduct(owner.getKey(), mktProduct2);
        PoolDTO pool1 = ownerApi.createPool(owner.getKey(), Pools.random(mktProduct1));
        PoolDTO pool2 = ownerApi.createPool(owner.getKey(), Pools.random(mktProduct2));

        ConsumerInstalledProductDTO installed = new ConsumerInstalledProductDTO()
            .productId(engProduct.getId())
            .productName(engProduct.getName());
        ConsumerDTO consumer = Consumers.random(owner)
            .facts(Map.of("virt.is_guest", "true"))
            .serviceLevel("unsatisfied_sla")
            .role("unsatisfied_role")
            .usage("unsatisfied_usage")
            .serviceType("unsatisfiedSupport")
            .installedProducts(Set.of(installed));
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        consumerClient.consumers().autoBind(consumer.getUuid());
        assertThat(consumerClient.consumers().listEntitlements(consumer.getUuid()))
            .singleElement()
            .returns(pool1.getId(), x -> x.getPool().getId());
    }

    @Test
    public void shouldAllowAConsumerAutoAttachWhenSlaOfCurrentEntitlementIsExempt() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO product1 = Products.randomEng()
            .addAttributesItem(ProductAttributes.SupportLevel.withValue("Standard"));
        product1 = ownerProductApi.createProduct(owner.getKey(), product1);
        ProductDTO product2 = Products.randomEng()
            .addAttributesItem(ProductAttributes.SupportLevel.withValue("Layered"))
            .addAttributesItem(ProductAttributes.SupportLevelExempt.withValue("true"));
        product2 = ownerProductApi.createProduct(owner.getKey(), product2);
        ownerApi.createPool(owner.getKey(), Pools.random(product1));
        ownerApi.createPool(owner.getKey(), Pools.random(product2));

        ConsumerInstalledProductDTO installed1 = new ConsumerInstalledProductDTO()
            .productId(product1.getId())
            .productName(product1.getName());
        ConsumerInstalledProductDTO installed2 = new ConsumerInstalledProductDTO()
            .productId(product2.getId())
            .productName(product2.getName());
        ConsumerDTO consumer = Consumers.random(owner)
            .addAddOnsItem("addon1")
            .addAddOnsItem("addon2")
            .installedProducts(Set.of(installed1, installed2));
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        assertThat(consumerClient.consumers().autoBind(consumer.getUuid())).hasSize(2);
        EntitlementDTO ent = consumerClient.consumers()
            .listEntitlements(consumer.getUuid(), product1.getId()).get(0);
        consumerClient.consumers().unbindByEntitlementId(consumer.getUuid(), ent.getId());
        assertThat(consumerClient.consumers().listEntitlements(consumer.getUuid())).singleElement();

        consumerClient.consumers().autoBind(consumer.getUuid());
        assertThat(consumerClient.consumers().listEntitlements(consumer.getUuid())).hasSize(2);
    }

    @Test
    public void shouldRemoveDuplicatesFromTheStackedPoolsProvideIdenticalRolesAddonsConsumerHasSpecified() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO engProduct = ownerProductApi.createProduct(owner.getKey(), Products.randomEng());
        ProductDTO product1 = Products.randomEng()
            .addAttributesItem(ProductAttributes.Roles.withValue("my_role"))
            .addAttributesItem(ProductAttributes.Addons.withValue("my_addon"))
            .providedProducts(Set.of(engProduct));
        product1 = ownerProductApi.createProduct(owner.getKey(), product1);
        ProductDTO product2 = Products.randomEng()
            .addAttributesItem(ProductAttributes.Roles.withValue("my_role"))
            .addAttributesItem(ProductAttributes.Addons.withValue("my_addon"))
            .providedProducts(Set.of(engProduct));
        product2 = ownerProductApi.createProduct(owner.getKey(), product2);
        ownerApi.createPool(owner.getKey(), Pools.random(product1));
        ownerApi.createPool(owner.getKey(), Pools.random(product2));

        ConsumerInstalledProductDTO installed = new ConsumerInstalledProductDTO()
            .productId(engProduct.getId())
            .productName(engProduct.getName());
        ConsumerDTO consumer = Consumers.random(owner)
            .addAddOnsItem("my_addon")
            .serviceLevel("my_sla")
            .role("my_role")
            .usage("my_usage")
            .installedProducts(Set.of(installed));
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        assertThat(consumerClient.consumers().autoBind(consumer.getUuid())).hasSize(1);
    }

    @Test
    public void shouldPoolsWithCaseInsensitivelyMatchingRoleOrAddonBeAttached() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));

        ProductDTO engProduct = ownerProductApi.createProduct(owner.getKey(), Products.randomEng());

        ProductDTO product1 = Products.random()
            .addAttributesItem(ProductAttributes.Roles.withValue("pRoViDeD_rOlE,non_provided_role"));
        product1 = ownerProductApi.createProduct(owner.getKey(), product1);
        PoolDTO poolWithRoleOnly = ownerApi.createPool(owner.getKey(), Pools.random(product1));

        ProductDTO product2 = Products.random()
            .addAttributesItem(ProductAttributes.Addons.withValue("pRoViDeD_aDDoN,non_provided_addon"));
        product2 = ownerProductApi.createProduct(owner.getKey(), product2);
        PoolDTO poolWithAddonOnly = ownerApi.createPool(owner.getKey(), Pools.random(product2));

        ConsumerInstalledProductDTO installed = new ConsumerInstalledProductDTO()
            .productId(engProduct.getId())
            .productName(engProduct.getName());
        ConsumerDTO consumer = Consumers.random(owner)
            .role("PROVIDED_ROLE")
            .addAddOnsItem("PROVIDED_ADDON")
            .addAddOnsItem("ANOTHER_ADDON")
            .installedProducts(Set.of(installed));
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        SystemPurposeComplianceStatusDTO status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("mismatched");
        assertThat(status.getNonCompliantRole()).isEqualTo("PROVIDED_ROLE");
        assertThat(status.getNonCompliantAddOns())
            .hasSize(2)
            .contains("PROVIDED_ADDON")
            .contains("ANOTHER_ADDON");

        assertThat(consumerClient.consumers().autoBind(consumer.getUuid())).hasSize(2);

        status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("mismatched");
        assertThat(status.getNonCompliantAddOns())
            .hasSize(1)
            .contains("ANOTHER_ADDON");
        assertThat(status.getCompliantAddOns().get("PROVIDED_ADDON"))
            .singleElement()
            .returns(poolWithAddonOnly.getId(), x -> x.getPool().getId());
        assertThat(status.getCompliantRole().get("PROVIDED_ROLE"))
            .singleElement()
            .returns(poolWithRoleOnly.getId(), x -> x.getPool().getId());
    }

    @Test
    public void shouldVirtualSystemBindToVirtOnlyPoolDespiteLackingSyspurposeAttributeMatches() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO engProduct = ownerProductApi.createProduct(owner.getKey(), Products.randomEng());
        ProductDTO mktProduct1 = Products.random()
            .providedProducts(Set.of(engProduct));
        mktProduct1 = ownerProductApi.createProduct(owner.getKey(), mktProduct1);
        ProductDTO mktProduct2 = Products.random()
            .addAttributesItem(ProductAttributes.VirtualOnly.withValue("true"))
            .addAttributesItem(ProductAttributes.Roles.withValue("SP Server"))
            .addAttributesItem(ProductAttributes.SupportLevel.withValue("provided_sla"))
            .addAttributesItem(ProductAttributes.Usage.withValue("Production"))
            .addAttributesItem(ProductAttributes.Addons.withValue("provided_addon"))
            .addAttributesItem(ProductAttributes.SupportType.withValue("test_support"))
            .providedProducts(Set.of(engProduct));
        mktProduct2 = ownerProductApi.createProduct(owner.getKey(), mktProduct2);
        ownerApi.createPool(owner.getKey(), Pools.random(mktProduct1));
        ownerApi.createPool(owner.getKey(), Pools.random(mktProduct2));

        ConsumerInstalledProductDTO installed = new ConsumerInstalledProductDTO()
            .productId(engProduct.getId())
            .productName(engProduct.getName());
        ConsumerDTO consumer = Consumers.random(owner)
            .facts(Map.of("virt.is_guest", "true"))
            .installedProducts(Set.of(installed));
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        consumerClient.consumers().autoBind(consumer.getUuid());
        assertThat(consumerClient.consumers().listEntitlements(consumer.getUuid()))
            .singleElement()
            .returns(mktProduct2.getId(), x -> x.getPool().getProductId());
    }

    @Test
    public void shouldPoolsWithWhiteSpacesInRoleOrAddonsBeAttached() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));

        ProductDTO engProduct = ownerProductApi.createProduct(owner.getKey(), Products.randomEng());

        ProductDTO product1 = Products.random()
            .addAttributesItem(ProductAttributes.Roles.withValue("PROVIDED_ROLE,    NON_PROVIDED_ROLE "));
        product1 = ownerProductApi.createProduct(owner.getKey(), product1);
        PoolDTO poolWithRoleOnly = ownerApi.createPool(owner.getKey(), Pools.random(product1));

        ProductDTO product2 = Products.random()
            .addAttributesItem(ProductAttributes.Addons.withValue("PROVIDED_ADDON,   NON_PROVIDED_ADDON "));
        product2 = ownerProductApi.createProduct(owner.getKey(), product2);
        PoolDTO poolWithAddonOnly = ownerApi.createPool(owner.getKey(), Pools.random(product2));

        ConsumerInstalledProductDTO installed = new ConsumerInstalledProductDTO()
            .productId(engProduct.getId())
            .productName(engProduct.getName());
        ConsumerDTO consumer = Consumers.random(owner)
            .role("PROVIDED_ROLE")
            .addAddOnsItem("PROVIDED_ADDON")
            .addAddOnsItem("ANOTHER_ADDON")
            .installedProducts(Set.of(installed));
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        SystemPurposeComplianceStatusDTO status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("mismatched");
        assertThat(status.getNonCompliantRole()).isEqualTo("PROVIDED_ROLE");
        assertThat(status.getNonCompliantAddOns())
            .hasSize(2)
            .contains("PROVIDED_ADDON")
            .contains("ANOTHER_ADDON");

        assertThat(consumerClient.consumers().autoBind(consumer.getUuid())).hasSize(2);

        status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("mismatched");
        assertThat(status.getNonCompliantAddOns())
            .hasSize(1)
            .contains("ANOTHER_ADDON");
        assertThat(status.getCompliantAddOns().get("PROVIDED_ADDON"))
            .singleElement()
            .returns(poolWithAddonOnly.getId(), x -> x.getPool().getId());
        assertThat(status.getCompliantRole().get("PROVIDED_ROLE"))
            .singleElement()
            .returns(poolWithRoleOnly.getId(), x -> x.getPool().getId());
    }

    @Test
    public void shouldBeNotSpecifiedWithConsumerOnAutoAttachWhenSlaOfCurrentEntitlementIsLayeredExempt() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO product1 = Products.randomEng()
            .addAttributesItem(ProductAttributes.SupportLevel.withValue("Standard"));
        product1 = ownerProductApi.createProduct(owner.getKey(), product1);
        ProductDTO product2 = Products.randomEng()
            .addAttributesItem(ProductAttributes.SupportLevel.withValue("Layered"))
            .addAttributesItem(ProductAttributes.SupportLevelExempt.withValue("true"));
        product2 = ownerProductApi.createProduct(owner.getKey(), product2);
        ownerApi.createPool(owner.getKey(), Pools.random(product1));
        ownerApi.createPool(owner.getKey(), Pools.random(product2));

        ConsumerInstalledProductDTO installed1 = new ConsumerInstalledProductDTO()
            .productId(product1.getId())
            .productName(product1.getName());
        ConsumerInstalledProductDTO installed2 = new ConsumerInstalledProductDTO()
            .productId(product2.getId())
            .productName(product2.getName());
        ConsumerDTO consumer = Consumers.random(owner)
            .serviceLevel("Layered")
            .installedProducts(Set.of(installed1, installed2));
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        consumerClient.consumers().bindProduct(consumer.getUuid(), product2.getId());
        SystemPurposeComplianceStatusDTO status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("not specified");

        final String consumerUuid = consumer.getUuid();
        consumerClient.consumers().listEntitlements(consumer.getUuid()).stream()
            .forEach(x -> consumerClient.consumers().unbindByEntitlementId(consumerUuid, x.getId()));

        // Trying to bind the product without SLA exempt and consumer with Layered SLA
        // Then system purpose status should be not specified
        ProductDTO product3 = Products.randomEng()
            .addAttributesItem(ProductAttributes.SupportLevel.withValue("Standard"))
            .addAttributesItem(ProductAttributes.SupportLevelExempt.withValue("false"));
        product3 = ownerProductApi.createProduct(owner.getKey(), product3);
        ownerApi.createPool(owner.getKey(), Pools.random(product3));
        consumerClient.consumers().bindProduct(consumer.getUuid(), product3.getId());

        status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("not specified");
    }


    @Test
    public void shouldPoolWithServiceTypeHavePriorityOverPoolWithout() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO engProduct = ownerProductApi.createProduct(owner.getKey(), Products.randomEng());
        ProductDTO mktProduct1 = Products.random()
            .addAttributesItem(ProductAttributes.SupportType.withValue("test_support"))
            .providedProducts(Set.of(engProduct));
        mktProduct1 = ownerProductApi.createProduct(owner.getKey(), mktProduct1);
        ProductDTO mktProduct2 = Products.random()
            .providedProducts(Set.of(engProduct));
        mktProduct2 = ownerProductApi.createProduct(owner.getKey(), mktProduct2);
        PoolDTO pool1 = ownerApi.createPool(owner.getKey(), Pools.random(mktProduct1));
        PoolDTO pool2 = ownerApi.createPool(owner.getKey(), Pools.random(mktProduct2));

        ConsumerInstalledProductDTO installed = new ConsumerInstalledProductDTO()
            .productId(engProduct.getId())
            .productName(engProduct.getName());
        ConsumerDTO consumer = Consumers.random(owner)
            .serviceType("test_support")
            .installedProducts(Set.of(installed));
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        SystemPurposeComplianceStatusDTO status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("mismatched");
        assertThat(status.getNonCompliantServiceType()).isEqualTo("test_support");

        assertThat(consumerClient.consumers().autoBind(consumer.getUuid())).hasSize(1);

        status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("matched");
        assertThat(status.getCompliantServiceType().get("test_support"))
            .singleElement()
            .returns(pool1.getId(), x -> x.getPool().getId());
    }

    @Test
    public void shouldPoolWithMatchingServiceTypeNotBeAttachedWhenItCoversNoProducts() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO engProduct = ownerProductApi.createProduct(owner.getKey(), Products.randomEng());
        ProductDTO mktProduct1 = Products.random()
            .addAttributesItem(ProductAttributes.SupportType.withValue("test_support"));
        mktProduct1 = ownerProductApi.createProduct(owner.getKey(), mktProduct1);
        PoolDTO pool1 = ownerApi.createPool(owner.getKey(), Pools.random(mktProduct1));

        ConsumerInstalledProductDTO installed = new ConsumerInstalledProductDTO()
            .productId(engProduct.getId())
            .productName(engProduct.getName());
        ConsumerDTO consumer = Consumers.random(owner)
            .serviceType("provided_service_type")
            .installedProducts(Set.of(installed));
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        SystemPurposeComplianceStatusDTO status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("mismatched");
        assertThat(status.getNonCompliantServiceType()).isEqualTo("provided_service_type");

        assertThat(consumerClient.consumers().autoBind(consumer.getUuid())).isEmpty();

        status =
            userClient.consumers().getSystemPurposeComplianceStatus(consumer.getUuid(), null);
        assertThat(status.getStatus()).isEqualTo("mismatched");
        assertThat(status.getNonCompliantServiceType()).isEqualTo("provided_service_type");
    }

    @Test
    public void shouldPoolWithVirtOnlyMatchNotOverpowerPoolWithServiceTypeMatch() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO engProduct = ownerProductApi.createProduct(owner.getKey(), Products.randomEng());
        ProductDTO mktProduct1 = Products.random()
            .addAttributesItem(ProductAttributes.SupportType.withValue("test_support"))
            .providedProducts(Set.of(engProduct));
        mktProduct1 = ownerProductApi.createProduct(owner.getKey(), mktProduct1);
        ProductDTO mktProduct2 = Products.random()
            .addAttributesItem(ProductAttributes.VirtualOnly.withValue("true"))
            .providedProducts(Set.of(engProduct));
        mktProduct2 = ownerProductApi.createProduct(owner.getKey(), mktProduct2);
        PoolDTO pool1 = ownerApi.createPool(owner.getKey(), Pools.random(mktProduct1));
        PoolDTO pool2 = ownerApi.createPool(owner.getKey(), Pools.random(mktProduct2));

        ConsumerInstalledProductDTO installed = new ConsumerInstalledProductDTO()
            .productId(engProduct.getId())
            .productName(engProduct.getName());
        ConsumerDTO consumer = Consumers.random(owner)
            .facts(Map.of("virt.is_guest", "true"))
            .serviceType("test_support")
            .installedProducts(Set.of(installed));
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        consumerClient.consumers().autoBind(consumer.getUuid());
        assertThat(consumerClient.consumers().listEntitlements(consumer.getUuid()))
            .singleElement()
            .returns(pool1.getId(), x -> x.getPool().getId());
    }


    private JsonNode getJsonNode(String consumerUuid) {
        return ApiClient.MAPPER.readTree(consumerUuid);
    }
}
