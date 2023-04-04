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
package org.candlepin.controller.mode;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Date;



/**
 * Container class for a suspend mode reason and message pairing
 */
public class ModeChangeReason {
    private final String reasonClass;
    private final String reason;
    private final Date time;
    private final String message;

    /**
     * Creates a new mode change reason with the given reason and message
     *
     * @param reasonClass
     *  A short string identifying the class of reason for entering suspend mode
     *
     * @param reason
     *  A short string identifying a specific reason for entering suspend mode
     *
     * @param time
     *  The date+time at which this reason triggered suspend mode
     *
     * @param message
     *  An optional, detailed message providing additional information for the reason Candlepin
     *  operations are suspended
     *
     * @throws IllegalArgumentException
     *  if either the provided reason class or string are null or empty
     */
    public ModeChangeReason(String reasonClass, String reason, Date time, String message) {
        if (reasonClass == null || reasonClass.isEmpty()) {
            throw new IllegalArgumentException("reason reasonClass is null or empty");
        }

        if (reason == null || reason.isEmpty()) {
            throw new IllegalArgumentException("reason string is null or empty");
        }

        if (time == null) {
            throw new IllegalArgumentException("time is null");
        }

        this.reasonClass = reasonClass;
        this.reason = reason;
        this.time = time;
        this.message = message;
    }

    /**
     * Fetches the class of reason for entering suspend mode.
     *
     * @return
     *  the class of reason for entering suspend mode
     */
    public String getReasonClass() {
        return this.reasonClass;
    }

    /**
     * Fetches the reason string for entering suspend mode
     *
     * @return
     *  the reason string for entering suspend mode
     */
    public String getReason() {
        return this.reason;
    }

    /**
     * Fetches the time this reason for entering suspend mode occurred
     *
     * @return
     *  the time this reason triggered suspend mode
     */
    public Date getTime() {
        return this.time;
    }

    /**
     * Fetches the extra, detailed message for this reason for entering suspend mode.
     *
     * @return
     *  the detail message for entering suspend mode
     */
    public String getMessage() {
        return this.message;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof ModeChangeReason) {
            ModeChangeReason that = (ModeChangeReason) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getReasonClass(), that.getReasonClass())
                .append(this.getReason(), that.getReason());

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
            .append(this.getReasonClass())
            .append(this.getReason());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        String message = this.getMessage();

        return message != null && !message.isEmpty() ?
            String.format("[%s] %s: %s", this.getReasonClass(), this.getReason(), message) :
            String.format("[%s] %s", this.getReasonClass(), this.getReason());
    }
}
