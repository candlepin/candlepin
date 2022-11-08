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

package org.candlepin.spec.entitlements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.ComplianceStatusDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerInstalledProductDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;


@SpecTest
public class VcpuSpecTest {

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
    public void shouldConsumerStatusBeValidWhenConsumerVCPUsAreCovered() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO vcpuProduct = createVcpuProuductAndPool(owner);

        ConsumerDTO system = userClient.consumers().createConsumer(
            Consumers.random(owner)
            .type(ConsumerTypes.System.value())
            .facts(Map.of("system.certificate_version", "3.2",
            // Simulate 8 cores as would be returned from system fact
            "cpu.core(s)_per_socket", "8",
            "virt.is_guest", "true"))
            .installedProducts(Set.of(new ConsumerInstalledProductDTO()
            .productId(vcpuProduct.getId()).productName(vcpuProduct.getName()))));
        ApiClient systemClient = ApiClients.ssl(system);
        assertNotNull(systemClient.consumers().bindProduct(system.getUuid(), vcpuProduct.getId()));

        ComplianceStatusDTO status = systemClient.consumers().getComplianceStatus(system.getUuid(), null);
        assertThat(status)
            .isNotNull()
            .returns("valid", ComplianceStatusDTO::getStatus)
            .returns(true, ComplianceStatusDTO::getCompliant)
            .doesNotReturn(null, x -> x.getCompliantProducts().get(vcpuProduct.getId()));
    }

    @Test
    public void shouldHealSingleEntitlementWhenVcpuLimited() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO vcpuProduct = createVcpuProuductAndPool(owner);

        ConsumerDTO system = userClient.consumers().createConsumer(
            Consumers.random(owner)
            .type(ConsumerTypes.System.value())
            .facts(Map.of("system.certificate_version", "3.2",
            // Simulate 8 cores as would be returned from system fact
            "cpu.core(s)_per_socket", "8",
            "virt.is_guest", "true"))
            .installedProducts(Set.of(new ConsumerInstalledProductDTO()
            .productId(vcpuProduct.getId()).productName(vcpuProduct.getName()))));
        ApiClient systemClient = ApiClients.ssl(system);
        // Perform healing
        assertThat(systemClient.consumers().autoBind(system.getUuid())).hasSize(1);
    }

    @Test
    public void shouldHealCorrectQuantityWhenVcpuLimited() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO vcpuStackableProduct = createVcpuStackableProductAndPool(owner);

        ConsumerDTO system = userClient.consumers().createConsumer(
            Consumers.random(owner)
            .type(ConsumerTypes.System.value())
            .facts(Map.of("system.certificate_version", "3.2",
            // Simulate 32 cores as would be returned from system fact
            "cpu.core(s)_per_socket", "32",
            "virt.is_guest", "true"))
            .installedProducts(Set.of(new ConsumerInstalledProductDTO()
            .productId(vcpuStackableProduct.getId()).productName(vcpuStackableProduct.getName()))));
        ApiClient systemClient = ApiClients.ssl(system);
        // Perform healing
        assertThat(systemClient.consumers().autoBind(system.getUuid()))
            .singleElement()
            .returns(4, x -> x.get("quantity").asInt());
    }


    @Test
    public void shouldNotHealWhenSystemVcpuIsNotCoveredByAnyEntitlements() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO vcpuProduct = createVcpuProuductAndPool(owner);

        ConsumerDTO system = userClient.consumers().createConsumer(
            Consumers.random(owner)
            .type(ConsumerTypes.System.value())
            .facts(Map.of("system.certificate_version", "3.2",
            // Simulate 12 cores as would be returned from system fact
            "cpu.core(s)_per_socket", "12",
            "virt.is_guest", "true"))
            .installedProducts(Set.of(new ConsumerInstalledProductDTO()
            .productId(vcpuProduct.getId()).productName(vcpuProduct.getName()))));
        ApiClient systemClient = ApiClients.ssl(system);
        assertThat(systemClient.consumers().autoBind(system.getUuid())).hasSize(0);
    }

    private ProductDTO createVcpuProuductAndPool(OwnerDTO owner) {
        ProductDTO vcpuProduct = ownerProductApi.createProductByOwner(owner.getKey(),
            Products.random()
            .addAttributesItem(new AttributeDTO().name("version").value("6.4"))
            .addAttributesItem(new AttributeDTO().name("vcpu").value("8"))
            .addAttributesItem(new AttributeDTO().name("cores").value("2"))
            .addAttributesItem(new AttributeDTO().name("sockets").value("1"))
            .addAttributesItem(new AttributeDTO().name("warning_period").value("15"))
            .addAttributesItem(new AttributeDTO().name("management_enabled").value("true"))
            .addAttributesItem(new AttributeDTO().name("support_level").value("standard"))
            .addAttributesItem(new AttributeDTO().name("support_type").value("excellent")));
        ownerApi.createPool(owner.getKey(), Pools.random().productId(vcpuProduct.getId()));
        return vcpuProduct;
    }

    private ProductDTO createVcpuStackableProductAndPool(OwnerDTO owner) {
        ProductDTO vcpuStackableProduct =  ownerProductApi.createProductByOwner(owner.getKey(),
            Products.random()
            .addAttributesItem(new AttributeDTO().name("version").value("6.4"))
            .addAttributesItem(new AttributeDTO().name("vcpu").value("8"))
            .addAttributesItem(new AttributeDTO().name("cores").value("2"))
            .addAttributesItem(new AttributeDTO().name("sockets").value("1"))
            .addAttributesItem(new AttributeDTO().name("warning_period").value("15"))
            .addAttributesItem(new AttributeDTO().name("management_enabled").value("true"))
            .addAttributesItem(new AttributeDTO().name("support_level").value("standard"))
            .addAttributesItem(new AttributeDTO().name("support_type").value("excellent"))
            .addAttributesItem(new AttributeDTO().name("multi-entitlement").value("yes"))
            .addAttributesItem(new AttributeDTO().name("stacking_id").value("12344321")));
        ownerApi.createPool(owner.getKey(), Pools.random().productId(vcpuStackableProduct.getId()));
        return vcpuStackableProduct;
    }
}
