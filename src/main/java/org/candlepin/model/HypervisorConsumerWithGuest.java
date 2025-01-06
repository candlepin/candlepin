/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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
package org.candlepin.model;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Internal POJO class for encapsulating a guest {@link Consumer} information with the most recent hypervisor
 * {@link Consumer} information.
 */
public class HypervisorConsumerWithGuest {

    private String hypervisorConsumerUuid;
    private String hypervisorConsumerName;
    private String guestConsumerUuid;
    private String guestId;

    public HypervisorConsumerWithGuest(String hypervisorConsumerUuid, String hypervisorConsumerName,
        String guestConsumerUuid, String guestId) {

        this.hypervisorConsumerUuid = hypervisorConsumerUuid;
        this.hypervisorConsumerName = hypervisorConsumerName;
        this.guestConsumerUuid = guestConsumerUuid;
        this.guestId = guestId;
    }

    /**
     * @return the hypervisor {@link Consumer} UUID
     */
    public String getHypervisorConsumerUuid() {
        return hypervisorConsumerUuid;
    }

    /**
     * Sets the hypervisor's {@link Consumer} UUID.
     *
     * @param hypervisorConsumerUuid
     *  the UUID of the hypervisor's consumer
     *
     * @return this instance
     */
    public HypervisorConsumerWithGuest setHypervisorConsumerUuid(String hypervisorConsumerUuid) {
        this.hypervisorConsumerUuid = hypervisorConsumerUuid;
        return this;
    }

    /**
     * @return the hypervisor {@link Consumer} name
     */
    public String getHypervisorConsumerName() {
        return hypervisorConsumerName;
    }

    /**
     * Sets the hypervisor's {@link Consumer} name.
     *
     * @param hypervisorConsumerName
     *  the name of the hypervisor's consumer
     *
     * @return this instance
     */
    public HypervisorConsumerWithGuest setHypervisorConsumerName(String hypervisorConsumerName) {
        this.hypervisorConsumerName = hypervisorConsumerName;
        return this;
    }

    /**
     * @return the guest {@link Consumer} UUID
     */
    public String getGuestConsumerUuid() {
        return guestConsumerUuid;
    }

    /**
     * Sets the guest's {@link Consumer} UUID.
     *
     * @param guestConsumerUuid
     *  the UUID of the guest consumer
     *
     * @return this instance
     */
    public HypervisorConsumerWithGuest setGuestConsumerUuid(String guestConsumerUuid) {
        this.guestConsumerUuid = guestConsumerUuid;
        return this;
    }

    /**
     * @return the guest ID. This ID is the {@link GuestId} getGuestId value.
     */
    public String getGuestId() {
        return guestId;
    }

    /**
     * Sets the guest's ID. This ID is the {@link GuestId} getGuestId value.
     *
     * @param guestId
     *  the ID of the guest
     *
     * @return this instance
     */
    public HypervisorConsumerWithGuest setGuestId(String guestId) {
        this.guestId = guestId;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof HypervisorConsumerWithGuest)) {
            return false;
        }

        HypervisorConsumerWithGuest other = (HypervisorConsumerWithGuest) object;

        EqualsBuilder builder = new EqualsBuilder()
            .append(this.getHypervisorConsumerUuid(), other.getHypervisorConsumerUuid())
            .append(this.getHypervisorConsumerName(), other.getHypervisorConsumerName())
            .append(this.getGuestConsumerUuid(), other.getGuestConsumerUuid())
            .append(this.getGuestId(), other.getGuestId());

        return builder.isEquals();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(this.getHypervisorConsumerUuid())
            .append(this.getHypervisorConsumerName())
            .append(this.getGuestConsumerUuid())
            .append(this.getGuestId());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("HypervisorConsumerWithGuest [" +
            "hypervisorConsumerUuid: %s, " +
            "hypervisorConsumerName: %s, " +
            "guestUuid: %s, " +
            "guestId: %s]", hypervisorConsumerUuid, hypervisorConsumerName, guestConsumerUuid, guestId);
    }

}
