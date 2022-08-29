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

import org.candlepin.ApiClient;
import org.candlepin.ApiException;
import org.candlepin.dto.api.v1.ConsumerDTOArrayElement;
import org.candlepin.dto.api.v1.PoolDTO;
import org.candlepin.resource.OwnerApi;

import java.util.List;
import java.util.Set;

public class OwnerClient extends OwnerApi {

    public OwnerClient(ApiClient client) {
        super(client);
    }

    public List<ConsumerDTOArrayElement> listOwnerConsumers(String ownerKey) throws ApiException {
        return listOwnerConsumers(ownerKey, Set.of());
    }

    public List<ConsumerDTOArrayElement> listOwnerConsumers(
        String ownerKey, Set<String> consumerTypes) throws ApiException {
        return super.listConsumers(ownerKey, null, consumerTypes, List.of(), List.of(), List.of(),
            null, null, null, null);
    }

    public List<PoolDTO> listOwnerPools(String ownerKey) throws ApiException {
        return super.listOwnerPools(
            ownerKey,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    }

    public List<PoolDTO> listOwnerPools(String ownerKey, Paging paging) throws ApiException {
        return super.listOwnerPools(
            ownerKey,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            paging.page(),
            paging.perPage(),
            paging.order(),
            paging.orderBy());
    }

    public List<PoolDTO> listOwnerPools(String ownerKey, String consumerUuid) throws ApiException {
        return super.listOwnerPools(
            ownerKey,
            consumerUuid,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    }

    public List<PoolDTO> listOwnerPools(
        String ownerKey, String consumerUuid, Paging paging) throws ApiException {
        return super.listOwnerPools(
            ownerKey,
            consumerUuid,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            paging.page(),
            paging.perPage(),
            paging.order(),
            paging.orderBy());
    }

}
