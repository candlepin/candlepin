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

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTOArrayElement;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.invoker.client.ApiClient;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.resource.client.v1.OwnerApi;

import java.io.File;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

public class OwnerClient extends OwnerApi {

    public OwnerClient(ApiClient client) {
        super(client);
    }

    public List<ConsumerDTOArrayElement> listOwnerConsumers(String ownerKey) {
        return listOwnerConsumers(ownerKey, Set.of());
    }

    public List<ConsumerDTOArrayElement> listOwnerConsumers(
        String ownerKey, Set<String> consumerTypes) {
        return super.listConsumers(ownerKey, null, consumerTypes, List.of(), List.of(), List.of(),
            null, null, null, null);
    }

    public List<PoolDTO> listOwnerPools(String ownerKey) {
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

    public List<PoolDTO> listOwnerPools(String ownerKey, Paging paging) {
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

    public List<PoolDTO> listOwnerPools(String ownerKey, String consumerUuid) {
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
        String ownerKey, String consumerUuid, Paging paging) {
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

    public List<PoolDTO> listOwnerPoolsWithAttributes(
        String ownerKey, List<String> attribute) throws ApiException {
        return super.listOwnerPools(
            ownerKey,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            attribute,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    }

    public List<PoolDTO> listOwnerPoolsByProductWithAttributes(
        String ownerKey, String productId, List<String> attributes) throws ApiException {
        return super.listOwnerPools(
            ownerKey,
            null,
            null,
            productId,
            null,
            null,
            null,
            null,
            attributes,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    }

    public List<PoolDTO> listOwnerPoolsByProduct(
        String ownerKey, String productId) throws ApiException {
        return super.listOwnerPools(
            ownerKey,
            null,
            null,
            productId,
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

    public List<PoolDTO> listOwnerPoolsWithPoolIds(String ownerKey, List<String> poolId)
        throws ApiException {
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
            poolId,
            null,
            null,
            null,
            null);
    }

    public List<PoolDTO> listOwnerPools(
        String ownerKey, Boolean addFuture, Boolean onlyFuture, OffsetDateTime after)
        throws ApiException {
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
            addFuture,
            onlyFuture,
            after,
            null,
            null,
            null,
            null,
            null);
    }

    public List<PoolDTO> listOwnerPoolsWithSubscription(
        String ownerKey, String subscription, Boolean addFuture) throws ApiException {
        return super.listOwnerPools(
            ownerKey,
            null,
            null,
            null,
            subscription,
            null,
            null,
            null,
            null,
            addFuture,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    }

    public AsyncJobStatusDTO importManifestAsync(String ownerKey, File input) throws ApiException {
        return super.importManifestAsync(ownerKey, List.of(), input);
    }
}
