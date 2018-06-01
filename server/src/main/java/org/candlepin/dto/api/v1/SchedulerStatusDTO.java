/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.CandlepinDTO;

/**
 * SchedulerStatusDTO
 */
public class SchedulerStatusDTO extends CandlepinDTO<SchedulerStatusDTO> {

    private Boolean isRunning;

    /**
     * Initializes a new SchedulerStatusDTO instance with null values.
     */
    public SchedulerStatusDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new SchedulerStatusDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public SchedulerStatusDTO(SchedulerStatusDTO source) {
        super(source);
    }

    /**
     * Sets the status of the scheduler (true for running, false otherwise).
     *
     * @param isRunning the isRunning to set
     *
     * @return a reference to this SchedulerStatusDTO object
     */
    public SchedulerStatusDTO setRunning(Boolean isRunning) {
        this.isRunning = isRunning;
        return this;
    }

    /**
     * Returns true if the scheduler is running, false otherwise.
     *
     * @return if the scheduler is running or not
     */
    public Boolean isRunning() {
        return isRunning;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("SchedulerStatusDTO [isRunning: %s]", this.isRunning());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof SchedulerStatusDTO) {
            SchedulerStatusDTO that = (SchedulerStatusDTO) obj;

            if (this.isRunning() == that.isRunning()) {
                return true;
            }

            return this.isRunning().equals(that.isRunning());
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        if (this.isRunning() == null) {
            return 0;
        }
        return this.isRunning().hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SchedulerStatusDTO clone() {
        return super.clone();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SchedulerStatusDTO populate(SchedulerStatusDTO source) {
        super.populate(source);

        this.setRunning(source.isRunning());
        return this;
    }
}
