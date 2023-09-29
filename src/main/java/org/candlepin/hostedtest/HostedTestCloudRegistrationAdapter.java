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
package org.candlepin.hostedtest;

import org.candlepin.service.CloudProvider;
import org.candlepin.service.CloudRegistrationAdapter;
import org.candlepin.service.exception.CloudRegistrationAuthorizationException;
import org.candlepin.service.exception.CouldNotAcquireCloudAccountLockException;
import org.candlepin.service.exception.CouldNotEntitleOrganizationException;
import org.candlepin.service.exception.MalformedCloudRegistrationException;
import org.candlepin.service.model.CloudAccountData;
import org.candlepin.service.model.CloudAuthenticationResult;
import org.candlepin.service.model.CloudRegistrationInfo;
import org.candlepin.service.model.OwnerInfo;
import org.candlepin.service.model.SubscriptionInfo;
import org.candlepin.util.ObjectMapperFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;



/**
 * The HostedTestProductServiceAdapter is a CloudRegistrationAdapter implementation backed by the
 * HostedTestDataStore upstream simulator.
 */
@Singleton
public class HostedTestCloudRegistrationAdapter implements CloudRegistrationAdapter {

    private static final String ACCOUNT_ID_FIELD_NAME = "accountId";
    private static final String INSTANCE_ID_FIELD_NAME = "instanceId";
    private static final String OFFERING_ID_FIELD_NAME = "cloudOfferingId";
    private static final ObjectMapper OBJ_MAPPER = ObjectMapperFactory.getObjectMapper();

    private final HostedTestDataStore datastore;

    @Inject
    public HostedTestCloudRegistrationAdapter(HostedTestDataStore datastore) {
        if (datastore == null) {
            throw new IllegalArgumentException("datastore is null");
        }

        this.datastore = datastore;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String resolveCloudRegistrationData(CloudRegistrationInfo cloudRegInfo)
        throws CloudRegistrationAuthorizationException, MalformedCloudRegistrationException {

        if (cloudRegInfo == null) {
            throw new MalformedCloudRegistrationException("No cloud registration information provided");
        }

        if (cloudRegInfo.getMetadata() == null) {
            throw new MalformedCloudRegistrationException(
                "No metadata provided with the cloud registration info");
        }

        // We don't care about the type or signature, just attempt to resolve the metadata to an
        // owner key
        OwnerInfo owner = this.datastore.getOwner(cloudRegInfo.getMetadata());
        return owner != null ? owner.getKey() : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudAuthenticationResult resolveCloudRegistrationDataV2(CloudRegistrationInfo cloudRegInfo)
        throws CloudRegistrationAuthorizationException, MalformedCloudRegistrationException {

        if (cloudRegInfo == null) {
            throw new MalformedCloudRegistrationException("No cloud registration information provided");
        }

        if (cloudRegInfo.getMetadata() == null) {
            throw new MalformedCloudRegistrationException(
                "No metadata provided with the cloud registration info");
        }

        Map<String, String> metadata = new HashMap<>();
        try {
            metadata = OBJ_MAPPER.readValue(cloudRegInfo.getMetadata(), Map.class);
        }
        catch (JsonProcessingException e) {
            throw new MalformedCloudRegistrationException(
                "unable to parse cloud registration information metadata");
        }

        String accountId = metadata.get(ACCOUNT_ID_FIELD_NAME);
        if (accountId == null || accountId.isBlank()) {
            throw new MalformedCloudRegistrationException(
                "missing cloud account ID in registration information metadata");
        }

        String instanceId = metadata.get(INSTANCE_ID_FIELD_NAME);
        if (instanceId == null || instanceId.isBlank()) {
            throw new MalformedCloudRegistrationException(
                "missing instance ID in registration information metadata");
        }

        String offerId = metadata.get(OFFERING_ID_FIELD_NAME);
        if (offerId == null || offerId.isBlank()) {
            throw new MalformedCloudRegistrationException(
                "missing offer ID in registration information metadata");
        }

        String ownerKey = this.datastore.getOwnerKeyForCloudAccountId(accountId);
        Set<String> productIds = this.datastore.getProductIdsForOfferId(offerId);
        boolean isEntitled = isOwnerEntitledToProducts(ownerKey, productIds);

        return new CloudAuthenticationResult() {
            @Override
            public String getCloudAccountId() {
                return accountId;
            }

            @Override
            public String getCloudInstanceId() {
                return instanceId;
            }

            @Override
            public CloudProvider getCloudProvider() {
                return CloudProvider.AWS;
            }

            @Override
            public String getOwnerKey() {
                return ownerKey;
            }

            @Override
            public String getOfferId() {
                return offerId;
            }

            @Override
            public Set<String> getProductIds() {
                return productIds;
            }

            @Override
            public boolean isEntitled() {
                return isEntitled;
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudAccountData setupCloudAccountOrg(String cloudAccountID, String cloudOfferingID,
        CloudProvider cloudProviderShortName, String ownerKey)
        throws CouldNotAcquireCloudAccountLockException, CouldNotEntitleOrganizationException {
        return new CloudAccountData("owner_key", false);
    }

    /**
     * Determines if the owner is entitled to all of the provided product IDs by verifying
     * that the owner has a subscription for each product.
     *
     * @param ownerKey
     *  the key of the owner that entitlements are being checked for
     *
     * @param productIds
     *  the IDs of all the products that must have a subscription
     *
     * @return true if the owner has a subscription for all product IDs, or false if a product
     *  does not have a subscription
     */
    private boolean isOwnerEntitledToProducts(String ownerKey, Collection<String> productIds) {
        if (ownerKey == null) {
            return false;
        }

        if (productIds == null || productIds.isEmpty()) {
            return true;
        }

        for (String prodId : productIds) {
            Set<String> subIds = this.datastore.productSubscriptionMap
                .getOrDefault(prodId, Collections.emptySet());

            boolean entitled = subIds.stream()
                .map(this.datastore::getSubscription)
                .filter(Objects::nonNull)
                .map(SubscriptionInfo::getOwner)
                .anyMatch(owner -> ownerKey.equals(owner.getKey()));

            if (!entitled) {
                return false;
            }
        }

        return true;
    }

}
