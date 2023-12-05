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
package org.candlepin.spec.consumers;

import static org.assertj.core.api.Assertions.assertThat;

import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

@SpecTest
public class DomainConsumerSpecTest {

    private static ApiClient admin;
    private OwnerDTO owner;
    private ApiClient userClient;

    @BeforeAll
    public static void beforeAll() {
        admin = ApiClients.admin();
    }

    @BeforeEach
    public void setUp() {
        this.owner = admin.owners().createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(admin, this.owner);
        this.userClient = ApiClients.basic(user);
    }

    @Test
    public void domainConsumerShouldNotBeAbleToConsumeNonDomainSpecificProducts() {
        ProductDTO product = createProduct();
        admin.owners().createPool(this.owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = this.userClient.consumers()
            .createConsumer(Consumers.random(this.owner, ConsumerTypes.Domain));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .bindProductSync(consumer.getUuid(), product);

        assertThat(entitlements)
            .isEmpty();
    }

    @Test
    public void domainConsumerShouldBeAbleToConsumeNonDomainSpecificProducts() {
        ConsumerDTO consumer = this.userClient.consumers()
            .createConsumer(Consumers.random(this.owner, ConsumerTypes.Domain));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        ProductDTO domainProduct = createDomainProduct();
        admin.owners().createPool(this.owner.getKey(), Pools.random(domainProduct));

        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .bindProductSync(consumer.getUuid(), domainProduct);

        assertThat(entitlements)
            .hasSize(1);
    }

    @Test
    public void nonDomainConsumerShouldNotBeAbleToConsumeDomainSpecificProducts() {
        ConsumerDTO consumer = this.userClient.consumers().createConsumer(Consumers.random(this.owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        ProductDTO domainProduct = createDomainProduct();
        admin.owners().createPool(this.owner.getKey(), Pools.random(domainProduct));

        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .bindProductSync(consumer.getUuid(), domainProduct);

        assertThat(entitlements)
            .isEmpty();
    }

    private ProductDTO createDomainProduct() {
        ProductDTO product = Products.withAttributes(
            ProductAttributes.RequiresConsumer.withValue(ConsumerTypes.Domain.name().toLowerCase()));
        return admin.ownerProducts().createProduct(this.owner.getKey(), product);
    }

    private ProductDTO createProduct() {
        return admin.ownerProducts()
            .createProduct(this.owner.getKey(), Products.random());
    }

}
