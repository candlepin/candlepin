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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.Date;

public class PoolExportProjectionTest {

    @Test
    public void testCopiesDatesOnConstructionAndAccess() {
        Date date = new Date(123456789L);
        PoolExportProjection projection = new PoolExportProjection("pool", "uuid", "product", null,
            date, date, date, date, null, null);
        date.setTime(0L);
        projection.created().setTime(0L);
        projection.updated().setTime(0L);
        projection.startDate().setTime(0L);
        projection.endDate().setTime(0L);

        assertThat(projection.created()).hasTime(123456789L);
        assertThat(projection.updated()).hasTime(123456789L);
        assertThat(projection.startDate()).hasTime(123456789L);
        assertThat(projection.endDate()).hasTime(123456789L);
    }

    @Test
    public void testAllowsAbsentDates() {
        PoolExportProjection projection = new PoolExportProjection("pool", "uuid", "product", null,
            null, null, null, null, null, null);

        assertThat(projection.created()).isNull();
        assertThat(projection.updated()).isNull();
        assertThat(projection.startDate()).isNull();
        assertThat(projection.endDate()).isNull();
    }
}
