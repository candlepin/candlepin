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

import org.candlepin.dto.api.v1.PoolDTO;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import java.time.OffsetDateTime;



/**
 * Class meant to provide fully randomized instances of pool.
 *
 * Individual tests can then modify the instance according to their needs.
 */
public final class Pools {

    private Pools() {
        throw new UnsupportedOperationException();
    }

    public static PoolDTO random() {
        return new PoolDTO()
            .startDate(OffsetDateTime.now())
            .endDate(OffsetDateTime.now().plusYears(10))
            .quantity(10L);
    }

    public static PoolDTO random(ProductDTO product) {
        return random()
            .productId(product.getId());
    }

    /**
     * Builds a pool with randomly generated attributes matching the format necessary for the pool
     * to be treated as one originating from an upstream source (manifest import/refresh).
     *
     * @param product
     *  the product the pool will be referencing as its SKU/marketing product
     *
     * @return
     *  a randomly generated upstream pool
     */
    public static PoolDTO randomUpstream(ProductDTO product) {
        String suffix = StringUtil.random(8, StringUtil.CHARSET_NUMERIC_HEX);

        return random(product)
            .subscriptionId("source_sub" + suffix)
            .subscriptionSubKey("master")
            .upstreamPoolId("upstream_pool_id" + suffix);
    }

}
