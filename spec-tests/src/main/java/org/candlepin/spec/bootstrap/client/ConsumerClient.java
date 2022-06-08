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
import org.candlepin.dto.api.v1.CertificateSerialDTO;
import org.candlepin.dto.api.v1.ComplianceStatusDTO;
import org.candlepin.dto.api.v1.ConsumerDTO;
import org.candlepin.dto.api.v1.ConsumerDTOArrayElement;
import org.candlepin.dto.api.v1.ContentAccessDTO;
import org.candlepin.dto.api.v1.ContentOverrideDTO;
import org.candlepin.dto.api.v1.DeleteResult;
import org.candlepin.dto.api.v1.DeletedConsumerDTO;
import org.candlepin.dto.api.v1.EntitlementDTO;
import org.candlepin.dto.api.v1.KeyValueParamDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.dto.api.v1.PoolQuantityDTO;
import org.candlepin.dto.api.v1.ReleaseVerDTO;
import org.candlepin.dto.api.v1.SystemPurposeComplianceStatusDTO;
import org.candlepin.resource.ConsumerApi;

import java.io.File;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConsumerClient extends ConsumerApi {

    public ConsumerClient(ApiClient client) {
        super(client);
    }

    @Override
    public List<ContentOverrideDTO> addConsumerContentOverrides(String consumerUuid,
        List<ContentOverrideDTO> contentOverrideDTOs) throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        if (contentOverrideDTOs == null) {
            throw new IllegalArgumentException("Content overrides must not be null.");
        }

        return super.addConsumerContentOverrides(consumerUuid, contentOverrideDTOs);
    }

    @Override
    public String bind(String consumerUuid, String pool, List<String> product, Integer quantity,
        String email, String emailLocale, Boolean async, String entitleDate, List<String> fromPool)
        throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        return super.bind(consumerUuid, pool, product, quantity, email, emailLocale, async,
            entitleDate, fromPool);
    }

    @Override
    public void consumerExists(String consumerUuid) throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        super.consumerExists(consumerUuid);
    }

    @Override
    public void consumerExistsBulk(Set<String> requestBody) throws ApiException {
        super.consumerExistsBulk(requestBody);
    }

    @Override
    public ConsumerDTO getConsumer(String consumerUuid) throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        return super.getConsumer(consumerUuid);
    }

    @Override
    public ConsumerDTO createConsumer(ConsumerDTO consumerDTO, String username, String owner,
        String activationKeys, Boolean identityCertCreation) throws ApiException {
        if (consumerDTO == null) {
            throw new IllegalArgumentException("Consumer must not be null.");
        }

        return super.createConsumer(consumerDTO, username, owner,
            activationKeys, identityCertCreation);
    }

    @Override
    public void deleteConsumer(String consumerUuid) throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        super.deleteConsumer(consumerUuid);
    }

    @Override
    public List<ContentOverrideDTO> deleteConsumerContentOverrides(String consumerUuid,
        List<ContentOverrideDTO> contentOverrideDTO) throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        if (contentOverrideDTO == null) {
            throw new IllegalArgumentException("Content overrides must not be null.");
        }

        return super.deleteConsumerContentOverrides(consumerUuid, contentOverrideDTO);
    }

    @Override
    public File downloadExistingExport(String consumerUuid, String exportId) throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        if (exportId == null || exportId.length() == 0) {
            throw new IllegalArgumentException("Export Id must not be null or empty.");
        }

        return super.downloadExistingExport(consumerUuid, exportId);
    }

    @Override
    public List<PoolQuantityDTO> dryBind(String consumerUuid, String serviceLevel) throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        return super.dryBind(consumerUuid, serviceLevel);
    }

    @Override
    public Object exportCertificates(String consumerUuid, String serials) throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        return super.exportCertificates(consumerUuid, serials);
    }

    @Override
    public File exportData(String consumerUuid, String cdnLabel, String webappPrefix, String apiUrl)
        throws ApiException {
        return super.exportData(consumerUuid, cdnLabel, webappPrefix, apiUrl);
    }

    @Override
    public ComplianceStatusDTO getComplianceStatus(String consumerUuid, String onDate) throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        return super.getComplianceStatus(consumerUuid, onDate);
    }

    @Override
    public Map<String, ComplianceStatusDTO> getComplianceStatusList(List<String> uuid) throws ApiException {
        return super.getComplianceStatusList(uuid);
    }

    @Override
    public String getContentAccessBody(String consumerUuid, OffsetDateTime ifModifiedSince)
        throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        return super.getContentAccessBody(consumerUuid, ifModifiedSince);
    }

    @Override
    public ContentAccessDTO getContentAccessForConsumer(String consumerUuid) throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        return super.getContentAccessForConsumer(consumerUuid);
    }

    @Override
    public List<CertificateSerialDTO> getEntitlementCertificateSerials(String consumerUuid)
        throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        return super.getEntitlementCertificateSerials(consumerUuid);
    }

    @Override
    public List<ConsumerDTOArrayElement> getGuests(String consumerUuid) throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        return super.getGuests(consumerUuid);
    }

    @Override
    public ConsumerDTO getHost(String consumerUuid) throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        return super.getHost(consumerUuid);
    }

    @Override
    public OwnerDTO getOwnerByConsumerUuid(String consumerUuid) throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        return super.getOwnerByConsumerUuid(consumerUuid);
    }

    @Override
    public ReleaseVerDTO getRelease(String consumerUuid) throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        return super.getRelease(consumerUuid);
    }

    @Override
    public SystemPurposeComplianceStatusDTO getSystemPurposeComplianceStatus(String consumerUuid,
        String onDate) throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        return super.getSystemPurposeComplianceStatus(consumerUuid, onDate);
    }

    @Override
    public List<DeletedConsumerDTO> listByDate(String date) throws ApiException {
        return super.listByDate(date);
    }

    @Override
    public List<ContentOverrideDTO> listConsumerContentOverrides(String consumerUuid) throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        return super.listConsumerContentOverrides(consumerUuid);
    }

    @Override
    public List<EntitlementDTO> listEntitlements(String consumerUuid, String product, Boolean regen,
        List<KeyValueParamDTO> attribute) throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        return super.listEntitlements(consumerUuid, product, regen, attribute);
    }

    @Override
    public void regenerateEntitlementCertificates(String consumerUuid, String entitlement, Boolean lazyRegen)
        throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        super.regenerateEntitlementCertificates(consumerUuid, entitlement, lazyRegen);
    }

    @Override
    public ConsumerDTO regenerateIdentityCertificates(String consumerUuid) throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        return super.regenerateIdentityCertificates(consumerUuid);
    }

    @Override
    public void removeDeletionRecord(String consumerUuid) throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        super.removeDeletionRecord(consumerUuid);
    }

    @Override
    public List<ConsumerDTOArrayElement> searchConsumers(String username, Set<String> type, String owner,
        List<String> uuid, List<String> hypervisorId, List<KeyValueParamDTO> fact) throws ApiException {
        return super.searchConsumers(username, type, owner, uuid, hypervisorId, fact);
    }

    @Override
    public DeleteResult unbindAll(String consumerUuid) throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        return super.unbindAll(consumerUuid);
    }

    @Override
    public void unbindByEntitlementId(String consumerUuid, String dbid) throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        if (dbid == null || dbid.length() == 0) {
            throw new IllegalArgumentException("Db Id must not be null or empty.");
        }

        super.unbindByEntitlementId(consumerUuid, dbid);
    }

    @Override
    public void unbindByPool(String consumerUuid, String poolId) throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        if (poolId == null || poolId.length() == 0) {
            throw new IllegalArgumentException("Pool Id must not be null or empty.");
        }

        super.unbindByPool(consumerUuid, poolId);
    }

    @Override
    public void unbindBySerial(String consumerUuid, Long serial) throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        if (serial == null) {
            throw new IllegalArgumentException("Serial must not be null.");
        }

        super.unbindBySerial(consumerUuid, serial);
    }

    @Override
    public void updateConsumer(String consumerUuid, ConsumerDTO consumerDTO) throws ApiException {
        if (consumerUuid == null || consumerUuid.length() == 0) {
            throw new IllegalArgumentException("Consumer Uuid must not be null or empty.");
        }

        if (consumerDTO == null) {
            throw new IllegalArgumentException("Consumer must not be null.");
        }

        super.updateConsumer(consumerUuid, consumerDTO);
    }
}
