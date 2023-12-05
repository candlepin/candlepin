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
package org.candlepin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.candlepin.dto.api.server.v1.NestedOwnerDTO;
import org.candlepin.dto.api.server.v1.ProductDTO;
import org.candlepin.dto.api.server.v1.SubscriptionDTO;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.resource.util.InfoAdapter;
import org.candlepin.service.model.SubscriptionInfo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;



@ExtendWith(MockitoExtension.class)
class PoolConverterTest {

    @Mock
    private OwnerCurator ownerCurator;
    @Mock
    private ProductCurator productCurator;
    @Mock
    private CdnCurator cdnCurator;

    private PoolConverter buildConverter() {
        return new PoolConverter(ownerCurator, productCurator, cdnCurator);
    }

    @Test
    void shouldRejectNullSubscription() {
        PoolConverter converter = this.buildConverter();

        assertThatThrownBy(() -> converter.convertToPrimaryPool(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectMissingOwner() {
        PoolConverter converter = this.buildConverter();

        SubscriptionDTO subscription = new SubscriptionDTO();
        SubscriptionInfo subscriptionInfo = InfoAdapter.subscriptionInfoAdapter(subscription);

        assertThatThrownBy(() -> converter.convertToPrimaryPool(subscriptionInfo))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldConvertSubscription() {
        PoolConverter converter = this.buildConverter();

        Owner owner = new Owner()
            .setKey("owner_key");

        ProductDTO product = new ProductDTO()
            .id("prod_id");

        SubscriptionDTO subscription = new SubscriptionDTO()
            .owner(new NestedOwnerDTO().key("owner_key"))
            .quantity(15L)
            .product(product);

        when(this.ownerCurator.getByKey(owner.getKey()))
            .thenReturn(owner);
        when(this.productCurator.resolveProductId(owner.getKey(), product.getId()))
            .thenReturn(new Product());

        SubscriptionInfo subscriptionInfo = InfoAdapter.subscriptionInfoAdapter(subscription);
        Pool pool = converter.convertToPrimaryPool(subscriptionInfo);

        assertThat(pool.getQuantity())
            .isEqualTo(subscriptionInfo.getQuantity());
    }

}
