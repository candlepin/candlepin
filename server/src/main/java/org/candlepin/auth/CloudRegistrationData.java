/**
 * Copyright (c) 2009 - 2021 Red Hat, Inc.
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
package org.candlepin.auth;

import org.candlepin.service.model.CloudRegistrationInfo;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;


/**
 * The CloudRegistrationData contains fields for performing automatic cloud-based registration.
 */
public class CloudRegistrationData implements CloudRegistrationInfo {

    private String type;
    private String metadata;
    private String signature;

    /**
     * Creates a new CloudRegistrationDTO instance with no values set
     */
    public CloudRegistrationData() {
        // Intentionally left empty
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getType() {
        return this.type;
    }

    /**
     * Sets or clears the cloud provider type
     *
     * @param type
     *  the cloud provider type
     *
     * @return
     *  a reference to this CloudRegistrationDTO
     */
    public CloudRegistrationData setType(String type) {
        this.type = type;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getMetadata() {
        return this.metadata;
    }

    /**
     * Sets or clears the registration metadata
     *
     * @param metadata
     *  the cloud registration metadata, such as the account holder identifiers or licenses
     *
     * @return
     *  a reference to this CloudRegistrationDTO
     */
    public CloudRegistrationData setMetadata(String metadata) {
        this.metadata = metadata;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSignature() {
        return this.signature;
    }

    /**
     * Sets or clears the cloud provider's signature
     *
     * @param signature
     *  the cloud provider's signature
     *
     * @return
     *  a reference to this CloudRegistrationDTO
     */
    public CloudRegistrationData setSignature(String signature) {
        this.signature = signature;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("CloudRegistrationDTO [type: %s, metadata: %s]",
            this.getType(), this.getMetadata());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof CloudRegistrationData) {
            CloudRegistrationData that = (CloudRegistrationData) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getType(), that.getType())
                .append(this.getMetadata(), that.getMetadata())
                .append(this.getSignature(), that.getSignature());

            return builder.isEquals();
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(this.getType())
            .append(this.getMetadata())
            .append(this.getSignature());

        return builder.toHashCode();
    }
}
