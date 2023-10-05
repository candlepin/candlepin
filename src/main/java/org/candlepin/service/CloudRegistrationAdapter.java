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

import org.candlepin.service.exception.cloudregistration.CloudRegistrationAuthorizationException;
import org.candlepin.service.exception.cloudregistration.CloudRegistrationServiceException;
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
     *  A CloudRegistrationInfo instance which contains the cloud provider details to process
     *
     * @throws CloudRegistrationAuthorizationException
     *  if cloud registration is not permitted for the provider or account holder
     *
     * @throws CloudRegistrationServiceException
     *  if there is an exception thrown in the method in the implementation. The subclass of
     *  the exception will give detail as to the problem
     *
     *  CloudRegistrationMissingTypeException
     *  if the cloud registration info does not contain a value for type
     *
     *  CloudRegistrationBadTypeException
     *  if the cloud registration info contains a value for type that is not on the list
     *
     *  CloudRegistrationBadMetadataException
     *  if the cloud registration info contains meta-data that will not allow for authentication
     *
     *  CloudRegistrationAuthorizationException
     *  if cloud registration is not permitted for the provider or account holder
     *
     *  CloudRegistrationUnknownDataException
     *  if cloud registration is stopped by an unexpected issue
     *
     * @return
     *  the owner key of the owner (organization) to which the cloud user will be registered
     */
    String resolveCloudRegistrationData(CloudRegistrationInfo cloudRegInfo)
        throws CloudRegistrationServiceException;
}
