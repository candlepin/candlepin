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
package org.candlepin.spec.consumers;

import static org.assertj.core.api.Assertions.assertThat;

import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

@SpecTest
public class ConsumerBonusPoolSpecTest {

    private static ApiClient admin;
    private OwnerDTO owner;
    private ApiClient ownerClient;

    @BeforeAll
    static void beforeAll() {
        admin = ApiClients.admin();
    }

    @BeforeEach
    void setUp() {
        this.owner = admin.owners().createOwner(Owners.random());
        this.ownerClient = ApiClients.basic(UserUtil.createAdminUser(admin, this.owner));
    }

    @Test
    @SuppressWarnings("indentation")
    public void bonusPoolShouldHaveProvidedProducts() {
        ProductDTO providedProduct = ownerClient.ownerProducts()
            .createProductByOwner(owner.getKey(), Products.random());
        ProductDTO product = ownerClient.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(
                ProductAttributes.VirtualLimit.withValue("5"),
                ProductAttributes.HostLimited.withValue("true")
            ).providedProducts(Set.of(providedProduct)));
        ownerClient.owners().createPool(owner.getKey(), Pools.randomUpstream(product));

        List<PoolDTO> pools = ownerClient.owners().listOwnerPools(this.owner.getKey());

        assertThat(pools)
            .hasSize(2)
            .allSatisfy(pool -> assertThat(pool.getProvidedProducts()).hasSize(1))
            .map(PoolDTO::getType)
            .containsExactlyInAnyOrder("NORMAL", "UNMAPPED_GUEST");
    }

}
