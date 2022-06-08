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

package org.candlepin.spec.bootstrap.client;

import org.candlepin.ApiClient;
import org.candlepin.ApiException;
import org.candlepin.dto.api.v1.CdnDTO;
import org.candlepin.dto.api.v1.EntitlementDTO;
import org.candlepin.dto.api.v1.PoolDTO;
import org.candlepin.resource.PoolsApi;

import java.util.List;

public class PoolClient extends PoolsApi {

    public PoolClient(ApiClient client) {
        super(client);
    }

    @Override
    public PoolDTO getPool(String poolId, String consumer, String activeon) throws ApiException {
        if (poolId == null || poolId.length() == 0) {
            throw new IllegalArgumentException("Pool Id must not be null or empty.");
        }

        return super.getPool(poolId, consumer, activeon);
    }

    @Override
    public CdnDTO getPoolCdn(String poolId) throws ApiException {
        if (poolId == null || poolId.length() == 0) {
            throw new IllegalArgumentException("Pool Id must not be null or empty.");
        }

        return super.getPoolCdn(poolId);
    }

    @Override
    public Object getSubCert(String poolId) throws ApiException {
        if (poolId == null || poolId.length() == 0) {
            throw new IllegalArgumentException("Pool Id must not be null or empty.");
        }

        return super.getSubCert(poolId);
    }

    @Override
    public List<EntitlementDTO> getPoolEntitlements(String poolId) throws ApiException {
        if (poolId == null || poolId.length() == 0) {
            throw new IllegalArgumentException("Pool Id must not be null or empty.");
        }

        return super.getPoolEntitlements(poolId);
    }

    @Override
    public List<String> listEntitledConsumerUuids(String poolId) throws ApiException {
        if (poolId == null || poolId.length() == 0) {
            throw new IllegalArgumentException("Pool Id must not be null or empty.");
        }

        return super.listEntitledConsumerUuids(poolId);
    }
}
