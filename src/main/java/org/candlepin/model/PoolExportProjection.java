/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

import java.util.Date;

/**
 * Scalar pool data required for a version 2 manifest export
 * <p>
 * Carries both the database product UUID and the public product ID without retaining managed entities.
 * Date values are copied on construction and access so the projection can be shared without mutation.
 *
 * @param poolId
 *     pool ID used as the export entry key
 * @param productUuid
 *     database product UUID used to seed recursive product loading
 * @param productId
 *     public product ID included in subscription data
 * @param subscriptionId
 *     source subscription ID, or null when absent
 * @param created
 *     pool creation timestamp
 * @param updated
 *     pool update timestamp
 * @param startDate
 *     subscription start date
 * @param endDate
 *     subscription end date
 * @param contractNumber
 *     contract number, or null when absent
 * @param accountNumber
 *     account number, or null when absent
 */
public record PoolExportProjection(
    String poolId,
    String productUuid,
    String productId,
    String subscriptionId,
    Date created,
    Date updated,
    Date startDate,
    Date endDate,
    String contractNumber,
    String accountNumber
) {
    /**
     * Copies mutable date values supplied by the query
     */
    public PoolExportProjection {
        created = PoolExportProjection.copyDate(created);
        updated = PoolExportProjection.copyDate(updated);
        startDate = PoolExportProjection.copyDate(startDate);
        endDate = PoolExportProjection.copyDate(endDate);
    }

    /**
     * Retrieves the pool creation timestamp
     *
     * @return a copy of the timestamp, or null when absent
     */
    @Override
    public Date created() {
        return PoolExportProjection.copyDate(this.created);
    }

    /**
     * Retrieves the pool update timestamp
     *
     * @return a copy of the timestamp, or null when absent
     */
    @Override
    public Date updated() {
        return PoolExportProjection.copyDate(this.updated);
    }

    /**
     * Retrieves the subscription start date
     *
     * @return a copy of the timestamp, or null when absent
     */
    @Override
    public Date startDate() {
        return PoolExportProjection.copyDate(this.startDate);
    }

    /**
     * Retrieves the subscription end date
     *
     * @return a copy of the timestamp, or null when absent
     */
    @Override
    public Date endDate() {
        return PoolExportProjection.copyDate(this.endDate);
    }

    private static Date copyDate(Date date) {
        return date != null ? (Date) date.clone() : null;
    }
}
