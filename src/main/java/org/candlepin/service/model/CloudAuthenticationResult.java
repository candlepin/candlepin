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
package org.candlepin.service.model;

import org.candlepin.service.CloudProvider;



/**
 * The cloud authentication result contains the results of authenticating
 * {@link CloudRegistrationInfo}.
 */
public interface CloudAuthenticationResult {

    /**
     * @return the cloud account ID that is found in the {@link CloudRegistrationInfo} metadata
     */
    String getCloudAccountId();

    /**
     * @return the cloud instance unique ID found the {@link CloudRegistrationInfo} metadata
     */
    String getCloudInstanceId();

    /**
     * @return the cloud provider found in the {@link CloudRegistrationInfo} metadata
     */
    CloudProvider getCloudProvider();

    /**
     * @return the owner key associated to the cloud account ID from the {@link CloudRegistrationInfo}
     * metadata, or null if there is no association
     */
    String getOwnerKey();

    /**
     * @return the cloud offering ID that is found in the {@link CloudRegistrationInfo} metadata
     */
    String getOfferId();

    /**
     * @return the product ID that is associated to the cloud offering ID that is found in the
     * {@link CloudRegistrationInfo} metadata
     */
    String getProductId();

    /**
     * @return true if the owner is entitled to the product associated to the cloud offering ID found in
     * the {@link CloudRegistrationInfo} metadata, or false if not entitled
     */
    boolean isEntitled();

}
