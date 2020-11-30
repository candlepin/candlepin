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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.cache.CacheManager;

/**
 * Wrapper that makes it easier to retrieve various caches in Candlepin
 * @author fnguyen
 *
 */
@Component
public class CandlepinCache {
    /**
     * Cache manager for Ehcache configured caches.
     */
    private CacheManager cacheManager;

    /**
     * We use our own version of a status cache as we ran
     * into some issues with ehcache-jcache impl.
     */
    private StatusCache statusCache;

    @Autowired
    public CandlepinCache(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
        // Safe to create this as many times as you'd like
        // since the same static Status instance will be
        // reused across all instances.
        this.statusCache = new StatusCache();
    }

    /**
     * Retrieves Candlepin Status cache. This cache will be used only to cache
     * status resource responses.
     *
     * @return StatusCache for Status entity
     */
    public StatusCache getStatusCache() {
        return this.statusCache;
    }

}
