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

package org.candlepin.spec.bootstrap.client.api;

import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.invoker.client.ApiClient;
import org.candlepin.resource.client.v1.PoolsApi;

import java.util.List;
import java.util.Map;

public class PoolsClient extends PoolsApi {

    public PoolsClient(ApiClient client) {
        super(client);
    }

    public List<PoolDTO> listPoolsByOwner(String ownerId) {
        return super.listPools(ownerId, null, null, true, null, null, null, null, null);
    }

    public List<PoolDTO> listPoolsByConsumer(String consumer) {
        return super.listPools(null, consumer, null, true, null, null, null, null, null);
    }

    public List<PoolDTO> listPoolsByConsumerAndProduct(String consumer, String productId) {
        return super.listPools(null, consumer, productId, true, null, null, null, null, null);
    }

    public List<PoolDTO> listPoolsByProduct(String ownerId, String productId) {
        return super.listPools(ownerId, null, productId, true, null, null, null, null, null);
    }

    public List<PoolDTO> listPoolsByOwnerAndProduct(String ownerId, String product) {
        return super.listPools(ownerId, null, product, true, null, null, null, null, null);
    }

    public Map<String, String> getCert(String poolId) {
        return (Map<String, String>) super.getSubCert(poolId);
    }
}
