/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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

package org.candlepin.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.candlepin.dto.api.v1.StatusDTO;

import org.junit.Before;
import org.junit.Test;

import java.time.OffsetDateTime;

/**
 * Created by mstead on 11/04/17.
 */
public class StatusCacheTest {

    @Before
    public void setup() {

    }

    @Test
    public void initialGetReturnsNull() {
        StatusCache cache = new StatusCache();
        assertNull(cache.getStatus());
    }

    @Test
    public void setAndGet() {
        StatusCache cache = new StatusCache();

        StatusDTO status = new StatusDTO()
            .result(true)
            .timeUTC(OffsetDateTime.now());

        cache.setStatus(status);
        assertEquals(status, cache.getStatus());
    }

    @Test
    public void testExpiresAfter5Seconds() throws Exception {
        StatusCache cache = new StatusCache();

        StatusDTO status = new StatusDTO()
            .result(true)
            .timeUTC(OffsetDateTime.now());

        cache.setStatus(status);
        assertEquals(status, cache.getStatus());
        Thread.sleep(6000L);
        assertNull(cache.getStatus());
    }

    @Test
    public void multipleInstancesShareSameStatus() {
        StatusCache cache1 = new StatusCache();
        StatusDTO status = new StatusDTO()
            .result(true)
            .timeUTC(OffsetDateTime.now());

        cache1.setStatus(status);

        StatusCache cache2 = new StatusCache();
        assertEquals(cache1.getStatus(), cache2.getStatus());
    }
}
