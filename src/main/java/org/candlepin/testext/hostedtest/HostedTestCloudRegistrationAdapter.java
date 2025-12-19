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
package org.candlepin.testext.hostedtest;

import org.candlepin.service.CloudRegistrationAdapter;
import org.candlepin.service.exception.cloudregistration.CloudRegistrationMalformedDataException;
import org.candlepin.service.exception.cloudregistration.CloudRegistrationNotSupportedForOfferingException;
import org.candlepin.service.exception.cloudregistration.CouldNotAcquireCloudAccountLockException;
import org.candlepin.service.exception.cloudregistration.CouldNotEntitleOrganizationException;
import org.candlepin.service.exception.cloudregistration.OrgForCloudAccountNotCreatedYetException;
import org.candlepin.service.exception.cloudregistration.OrgForCloudAccountNotEntitledYetException;
import org.candlepin.service.model.CloudAccountData;
import org.candlepin.service.model.CloudAuthenticationResult;
import org.candlepin.service.model.CloudRegistrationInfo;
import org.candlepin.service.model.OwnerInfo;
import org.candlepin.service.model.SubscriptionInfo;
import org.candlepin.util.ObjectMapperFactory;
import org.candlepin.util.Util;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
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
    private static final String OFFERING_TYPE_1P = "1P";
    private static final String OFFERING_TYPE_GOLD = "gold";
    private static final String OFFERING_TYPE_CUSTOM = "custom";
    private static final Set<String> REGISTRATION_ONLY_OFFER_TYPES =
        Set.of(OFFERING_TYPE_1P, OFFERING_TYPE_GOLD, OFFERING_TYPE_CUSTOM);

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
    public String resolveCloudRegistrationData(CloudRegistrationInfo cloudRegInfo) {

        if (cloudRegInfo == null) {
            throw new CloudRegistrationMalformedDataException("No cloud registration information provided");
        }

        if (cloudRegInfo.getMetadata() == null) {
            throw new CloudRegistrationMalformedDataException(
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
    public CloudAuthenticationResult resolveCloudRegistrationDataV2(CloudRegistrationInfo cloudRegInfo) {

        if (cloudRegInfo == null) {
            throw new CloudRegistrationMalformedDataException("No cloud registration information provided");
        }

        if (cloudRegInfo.getMetadata() == null) {
            throw new CloudRegistrationMalformedDataException(
                "No metadata provided with the cloud registration info");
        }

        Map<String, String> metadata = new HashMap<>();
        try {
            String decoded = new String(Base64.getDecoder().decode(cloudRegInfo.getMetadata()));
            metadata = OBJ_MAPPER.readValue(decoded, Map.class);
        }
        catch (JacksonException e) {
            throw new CloudRegistrationMalformedDataException(
                "unable to parse cloud registration information metadata");
        }

        String accountId = metadata.get(ACCOUNT_ID_FIELD_NAME);
        if (accountId == null || accountId.isBlank()) {
            throw new CloudRegistrationMalformedDataException(
                "missing cloud account ID in registration information metadata");
        }

        String instanceId = metadata.get(INSTANCE_ID_FIELD_NAME);
        if (instanceId == null || instanceId.isBlank()) {
            throw new CloudRegistrationMalformedDataException(
                "missing instance ID in registration information metadata");
        }

        String offerId = metadata.get(OFFERING_ID_FIELD_NAME);
        if (offerId == null || offerId.isBlank()) {
            throw new CloudRegistrationMalformedDataException(
                "missing offer ID in registration information metadata");
        }

        String offerType = this.datastore.getOfferTypeForOfferId(offerId);
        boolean isRegistrationOnly = offerType == null ?
            false :
            REGISTRATION_ONLY_OFFER_TYPES.contains(offerType);
        String ownerKey = this.datastore.getOwnerKeyForCloudAccountId(accountId);
        if (ownerKey == null && isRegistrationOnly) {
            throw new CloudRegistrationNotSupportedForOfferingException(
                "cloud registration v2 is not supported for 1P offerings");
        }

        Set<String> productIds = this.datastore.getProductIdsForOfferId(offerId);
        boolean isEntitled = isOwnerEntitledToProducts(ownerKey, productIds);

        return new CloudAuthenticationResult() {
            @Override
            public String getCloudAccountId() {
                return isRegistrationOnly ? null : accountId;
            }

            @Override
            public String getCloudInstanceId() {
                return isRegistrationOnly ? null : instanceId;
            }

            @Override
            public String getCloudProvider() {
                return isRegistrationOnly ? null : "aws";
            }

            @Override
            public String getOwnerKey() {
                return ownerKey;
            }

            @Override
            public String getOfferId() {
                return isRegistrationOnly ? null : offerId;
            }

            @Override
            public Set<String> getProductIds() {
                return isRegistrationOnly ? null : productIds;
            }

            @Override
            public boolean isRegistrationOnly() {
                return isRegistrationOnly;
            }

            @Override
            public boolean isEntitled() {
                return isRegistrationOnly || isEntitled;
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudAccountData setupCloudAccountOrg(String cloudAccountID, String cloudOfferingID,
        String cloudProviderShortName)
        throws CouldNotAcquireCloudAccountLockException, CouldNotEntitleOrganizationException {

        return new CloudAccountData(Util.generateUUID(), false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String checkCloudAccountOrgIsReady(String cloudAccountID, String cloudProviderShortName,
        String cloudOfferingID)
        throws OrgForCloudAccountNotCreatedYetException, OrgForCloudAccountNotEntitledYetException {
        String ownerKey = this.datastore.getOwnerKeyForCloudAccountId(cloudAccountID);

        if (ownerKey == null || ownerKey.isBlank()) {
            throw new OrgForCloudAccountNotCreatedYetException();
        }

        Set<String> productIdsForOfferId = this.datastore.getProductIdsForOfferId(cloudOfferingID);
        if (!isOwnerEntitledToProducts(ownerKey, productIdsForOfferId)) {
            throw new OrgForCloudAccountNotEntitledYetException();
        }

        return ownerKey;
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
