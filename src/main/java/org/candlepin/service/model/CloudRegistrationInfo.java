/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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



/**
 * The CloudRegistrationInfo represents a minimal set of cloud registration information to be used
 * for authenticating a given cloud provider and user account for automatic registration in
 * Candlepin.
 *
 * Data which is not set or does not change should be represented by null values. To explicitly
 * clear a value, an empty string or non-null "empty" value should be used instead.
 */
public interface CloudRegistrationInfo {

    /**
     * Fetches the cloud provider type.
     *
     * @return
     *  the cloud provider type, or null if the provider type has not been set
     */
    String getType();

    /**
     * Fetches the metadata for the cloud provider, such as the user's account identifiers.
     *
     * @return
     *  the cloud provider metadata, or null of the metadata has not been set
     */
    String getMetadata();

    /**
     * Fetches the signature to use for verifying the identity of the cloud provider.
     *
     * @return
     *  the cloud provider's signature, or null if the signature has not been set
     */
    String getSignature();

}
