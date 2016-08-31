/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import org.candlepin.model.Status;

import com.google.inject.Inject;

import javax.cache.Cache;
import javax.cache.CacheManager;

/**
 * Wrapper that makes it easier to retrieve various caches in Candlepin
 * @author fnguyen
 *
 */
public class CandlepinCache {
    private CacheManager cacheManager;
    public static final String STATUS_KEY = "status";

    @Inject
    public CandlepinCache(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Retrieves Candlepin Status cache. This cache will be used only to cache
     * status resource responses using single key STATUS_KEY
     *
     * @return Cache for Status entity
     */
    public Cache<String, Status> getStatusCache() {
        return cacheManager.getCache(CacheContextListener.CACHE_STATUS, String.class, Status.class);
    }
}
