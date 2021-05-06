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

import org.candlepin.dto.api.v1.StatusDTO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * Caches a {@link org.candlepin.dto.api.v1.StatusDTO} for 5 seconds.
 */
public class StatusCache {
    private static Logger log = LoggerFactory.getLogger(StatusCache.class);
    private static final Object LOCK = new Object();

    /** The Time-To-Live setting for our cached status object, in milliseconds */
    private static final int STATUS_CACHE_TTL = 5000;

    /** Cached status object; Will be discarded after STATUS_CACHE_TTL milliseconds */
    private static volatile StatusDTO cachedStatus;


    /**
     * Gets the current Status in the cache.
     * @return the current {@link Status}, null if it has not yet been set.
     */
    public StatusDTO getStatus() {
        synchronized (LOCK) {
            // Check if we can return our cached status
            if (cachedStatus != null && cachedStatus.getTimeUTC() != null) {
                long lastUpdateTimeSkew =
                    System.currentTimeMillis() - cachedStatus.getTimeUTC().toInstant().toEpochMilli();
                if (lastUpdateTimeSkew <= STATUS_CACHE_TTL) {
                    log.debug("Returning cached status. Last Update Date: {}, Age: {}ms",
                        cachedStatus.getTimeUTC(), lastUpdateTimeSkew);
                    return cachedStatus;
                }
                log.debug("Cache existed but had expired. Date: {}, Age: {}ms", cachedStatus.getTimeUTC(),
                    lastUpdateTimeSkew);
            }
            // If the cached value was invalidated, reset it.
            reset();
        }
        // Return null if the cached value was invalidated.
        return null;
    }

    /**
     * Sets the value of the cached status value.
     *
     * @param status the Status to set.
     */
    public void setStatus(StatusDTO status) {
        synchronized (LOCK) {
            cachedStatus = status;
        }
    }

    /**
     * Clear the cached status to allow forced regeneration.
     */
    public void clear() {
        synchronized (LOCK) {
            reset();
        }
    }

    private void reset() {
        cachedStatus = null;
    }
}
