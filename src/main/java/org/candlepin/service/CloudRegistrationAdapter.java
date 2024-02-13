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
package org.candlepin.service;

import org.candlepin.service.exception.cloudregistration.CloudAccountOrgMismatchException;
import org.candlepin.service.exception.cloudregistration.CouldNotAcquireCloudAccountLockException;
import org.candlepin.service.exception.cloudregistration.CouldNotEntitleOrganizationException;
import org.candlepin.service.exception.cloudregistration.OrgForCloudAccountNotCreatedYetException;
import org.candlepin.service.exception.cloudregistration.OrgForCloudAccountNotEntitledYetException;
import org.candlepin.service.model.CloudAccountData;
import org.candlepin.service.model.CloudAuthenticationResult;
import org.candlepin.service.model.CloudRegistrationInfo;

/**
 * The Cloud Registration Adapter provides the interface for verifying and resolving cloud
 * registration to a specific organization.
 */
public interface CloudRegistrationAdapter {

    /**
     * Resolves the cloud registration details to a specific organization.
     *
     * @param cloudRegInfo
     *     A CloudRegistrationInfo instance which contains the cloud provider details to process
     *
     * @return the owner key of the owner (organization) to which the cloud user will be registered
     */
    String resolveCloudRegistrationData(CloudRegistrationInfo cloudRegInfo);

    /**
     * Resolves the cloud registration details to a specific owner using version 2 logic.
     *
     * @param cloudRegInfo
     *     A CloudRegistrationInfo instance which contains the cloud provider details to process
     *
     * @return the cloud authentication result which contains the owner key, cloud account ID, and
     * product ID
     */
    CloudAuthenticationResult resolveCloudRegistrationDataV2(CloudRegistrationInfo cloudRegInfo);

    /**
     * Create (if one does not already exist) and entitle an owner upstream of Candlepin, for the given
     * cloud account id and cloud offering id.
     *
     * @param cloudAccountID
     *     Identifier for cloud account that needs access to Red Hat content
     *
     * @param cloudOfferingID
     *     Identifier for a Red Hat cloud offering that the owner needs to be entitled for
     *
     * @param cloudProviderShortName
     *     Shortcut of the cloud provider from which the offering came
     *
     * @throws CouldNotAcquireCloudAccountLockException
     *     Organization is already being created and/or entitled
     *
     * @throws CouldNotEntitleOrganizationException
     *     The organization could not be entitled
     *
     * @return key of the organization with entitled offering
     */
    CloudAccountData setupCloudAccountOrg(String cloudAccountID, String cloudOfferingID,
        String cloudProviderShortName)
        throws CouldNotAcquireCloudAccountLockException, CouldNotEntitleOrganizationException,
            CloudAccountOrgMismatchException;

    /**
     * Verify that cloud account have organization and have entitlements
     *
     * @param cloudAccountID
     *     Identifier for cloud account that needs access to Red Hat content
     *
     * @param cloudProviderShortName
     *     Shortcut of the cloud provider from which the offering came
     *
     * @param cloudOfferingID
     *     Identifier for a Red Hat cloud offering that the owner needs to be entitled for
     *
     * @throws OrgForCloudAccountNotCreatedYetException
     *     Organization does not exist yet
     *
     * @throws OrgForCloudAccountNotEntitledYetException
     *     Organization is not entitled yet
     *
     * @return key of the organization
     */
    String checkCloudAccountOrgIsReady(String cloudAccountID, String cloudProviderShortName,
        String cloudOfferingID) throws OrgForCloudAccountNotCreatedYetException,
        OrgForCloudAccountNotEntitledYetException;

}
