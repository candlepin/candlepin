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

import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerInstalledProductDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
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
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;


@SpecTest
public class RamSpecTest {

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
    public void shouldConsumeRamEntitlementIfRequestingV31Certificate() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        PoolDTO ramPool = createRamProductAndPool(owner);
        ConsumerDTO system = userClient.consumers().createConsumer(
            Consumers.random(owner).facts(Map.of("system.certificate_version", "3.1")));
        ApiClient systemClient = ApiClients.ssl(system);

        assertThat(systemClient.consumers().bindProduct(system.getUuid(), ramPool.getProductId()))
            .singleElement();
    }

    @Test
    public void shouldHealWhenRamLimited() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        PoolDTO ramPool = createRamSocketProductAndPool(owner);
        ConsumerDTO system = userClient.consumers().createConsumer(
            Consumers.random(owner)
            .facts(Map.of("system.certificate_version", "3.1",
            // Simulate 8 GB of RAM as would be returned from system fact (kb)
            "memory.memtotal", "8000000"))
            .installedProducts(Set.of(new ConsumerInstalledProductDTO()
            .productId(ramPool.getProductId())
            .productName(ramPool.getProductName()))));
        ApiClient systemClient = ApiClients.ssl(system);

        // Perform healing
        assertThat(systemClient.consumers().autoBind(system.getUuid()))
            .singleElement();
    }

    @Test
    public void shouldNotHealWhenSystemRamNotCoveredByAnyEntitlements() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        PoolDTO ramPool = createRamSocketProductAndPool(owner);
        ConsumerDTO system = userClient.consumers().createConsumer(
            Consumers.random(owner)
            .facts(Map.of("system.certificate_version", "3.1",
            // Simulate 12 GB of RAM as would be returned from system fact (kb)
            "memory.memtotal", "12000000"))
            .installedProducts(Set.of(new ConsumerInstalledProductDTO()
            .productId(ramPool.getProductId())
            .productName(ramPool.getProductName()))));
        ApiClient systemClient = ApiClients.ssl(system);

        // Perform healing
        assertThat(systemClient.consumers().autoBind(system.getUuid()))
            .isEmpty();
    }

    @Test
    public void shouldHealWhenBothRamAndSocketLimited() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        PoolDTO ramPool = createRamSocketProductAndPool(owner);
        ConsumerDTO system = userClient.consumers().createConsumer(
            Consumers.random(owner)
            .facts(Map.of("system.certificate_version", "3.1",
            // Simulate 8 GB of RAM as would be returned from system fact (kb)
            // which will be covered by the enitlement when consumed.
            "memory.memtotal", "8000000",
            // Simulate system having 4 sockets which will be covered after consuming
            // the entitlement
            "cpu.cpu_socket(s)", "4"))
            .installedProducts(Set.of(new ConsumerInstalledProductDTO()
            .productId(ramPool.getProductId())
            .productName(ramPool.getProductName()))));
        ApiClient systemClient = ApiClients.ssl(system);

        // Perform healing
        assertThat(systemClient.consumers().autoBind(system.getUuid()))
            .singleElement();
    }

    private PoolDTO createRamProductAndPool(OwnerDTO owner) {
        ProductDTO ramProduct = ownerProductApi.createProduct(owner.getKey(),
            Products.random()
            .addAttributesItem(ProductAttributes.Version.withValue("6.4"))
            .addAttributesItem(ProductAttributes.Ram.withValue("8"))
            .addAttributesItem(ProductAttributes.WarningPeriod.withValue("15"))
            .addAttributesItem(ProductAttributes.ManagementEnabled.withValue("true"))
            .addAttributesItem(ProductAttributes.SupportLevel.withValue("standard"))
            .addAttributesItem(ProductAttributes.SupportType.withValue("excellent")));
        return ownerApi.createPool(owner.getKey(),
            Pools.random(ramProduct).contractNumber("1888").accountNumber("1234"));
    }

    private PoolDTO createRamSocketProductAndPool(OwnerDTO owner) {
        ProductDTO ramSocketProduct = ownerProductApi.createProduct(owner.getKey(),
            Products.random()
            .addAttributesItem(ProductAttributes.Version.withValue("1.2"))
            .addAttributesItem(ProductAttributes.Ram.withValue("8"))
            .addAttributesItem(ProductAttributes.Sockets.withValue("4"))
            .addAttributesItem(ProductAttributes.WarningPeriod.withValue("15"))
            .addAttributesItem(ProductAttributes.ManagementEnabled.withValue("true"))
            .addAttributesItem(ProductAttributes.SupportLevel.withValue("standard"))
            .addAttributesItem(ProductAttributes.SupportType.withValue("excellent")));
        return ownerApi.createPool(owner.getKey(),
            Pools.random(ramSocketProduct).quantity(5L).contractNumber("18881").accountNumber("1222"));
    }

    private PoolDTO createStackableRamProductAndPool(OwnerDTO owner) {
        ProductDTO stackableRamProduct = ownerProductApi.createProduct(owner.getKey(),
            Products.random()
            .addAttributesItem(ProductAttributes.Version.withValue("1.2"))
            .addAttributesItem(ProductAttributes.Ram.withValue("2"))
            .addAttributesItem(ProductAttributes.WarningPeriod.withValue("15"))
            .addAttributesItem(ProductAttributes.SupportLevel.withValue("standard"))
            .addAttributesItem(ProductAttributes.SupportType.withValue("excellent"))
            .addAttributesItem(ProductAttributes.MultiEntitlement.withValue("yes"))
            .addAttributesItem(ProductAttributes.StackingId.withValue("2421")));
        return ownerApi.createPool(owner.getKey(), Pools.random(stackableRamProduct));
    }
}
