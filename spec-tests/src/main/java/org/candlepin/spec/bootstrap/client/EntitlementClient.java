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
import org.candlepin.dto.api.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.v1.EntitlementDTO;
import org.candlepin.dto.api.v1.KeyValueParamDTO;
import org.candlepin.resource.EntitlementsApi;

import java.util.List;

public class EntitlementClient extends EntitlementsApi {

    public EntitlementClient(ApiClient client) {
        super(client);
    }

    @Override
    public EntitlementDTO getEntitlement(String entitlementId) throws ApiException {
        if (entitlementId == null || entitlementId.length() == 0) {
            throw new IllegalArgumentException("Entitlement Id must not be null or empty.");
        }

        return super.getEntitlement(entitlementId);
    }

    @Override
    public String getUpstreamCert(String dbid) throws ApiException {
        if (dbid == null || dbid.length() == 0) {
            throw new IllegalArgumentException("Db Id must not be null or empty.");
        }

        return getUpstreamCert(dbid);
    }

    @Override
    public EntitlementDTO hasEntitlement(String consumerUuid, String productId) throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        if (productId == null || productId.length() == 0) {
            throw new IllegalArgumentException("Product Id must not be null or empty.");
        }

        return hasEntitlement(consumerUuid, productId);
    }

    @Override
    public List<EntitlementDTO> listAllForConsumer(String consumer, String matches,
        List<KeyValueParamDTO> attribute) throws ApiException {
        return listAllForConsumer(consumer, matches, attribute);
    }

    @Override
    public List<EntitlementDTO> migrateEntitlement(String entitlementId, String toConsumer, Integer quantity)
        throws ApiException {
        if (entitlementId == null || entitlementId.length() == 0) {
            throw new IllegalArgumentException("Entitlement Id must not be null or empty.");
        }

        return migrateEntitlement(entitlementId, toConsumer, quantity);
    }

    @Override
    public void updateEntitlement(String entitlementId, EntitlementDTO entitlementDTO) throws ApiException {
        if (entitlementId == null || entitlementId.length() == 0) {
            throw new IllegalArgumentException("Entitlement Id must not be null or empty.");
        }

        if (entitlementDTO == null) {
            throw new IllegalArgumentException("Entitlement must not be null.");
        }

        super.updateEntitlement(entitlementId, entitlementDTO);

    }

    @Override
    public AsyncJobStatusDTO regenerateEntitlementCertificatesForProduct(String productId, Boolean lazyRegen)
        throws ApiException {
        if (productId == null || productId.length() == 0) {
            throw new IllegalArgumentException("Product Id must not be null or empty.");
        }

        return super.regenerateEntitlementCertificatesForProduct(productId, lazyRegen);
    }

    @Override
    public void unbind(String dbid) throws ApiException {
        if (dbid == null || dbid.length() == 0) {
            throw new IllegalArgumentException("Db Id must not be null or empty.");
        }

        super.unbind(dbid);
    }
}
