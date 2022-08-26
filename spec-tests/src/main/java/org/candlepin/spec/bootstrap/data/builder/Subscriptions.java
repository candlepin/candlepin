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

package org.candlepin.spec.bootstrap.data.builder;

import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.dto.api.v1.SubscriptionDTO;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import java.time.OffsetDateTime;

/**
 * Class meant to provide fully randomized instances of Subscription.
 *
 * Individual tests can then modify the instance according to their needs.
 */
public final class Subscriptions {

    private Subscriptions() {
        throw new UnsupportedOperationException();
    }

    public static SubscriptionDTO random(OwnerDTO owner, ProductDTO product) {
        return new SubscriptionDTO()
            .id(StringUtil.random("test_sub-", 8, StringUtil.CHARSET_NUMERIC_HEX))
            .owner(Owners.toNested(owner))
            .product(product)
            .quantity(10L)
            .startDate(OffsetDateTime.now())
            .endDate(OffsetDateTime.now().plusYears(1));
    }

}
