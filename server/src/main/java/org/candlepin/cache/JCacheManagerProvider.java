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

import com.google.inject.Provider;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;

/**
 * Provides object cache by indexed by String.
 * @author fnguyen
 *
 */
public class JCacheManagerProvider implements Provider<CacheManager> {

    /**
     * It is safe to bind this as singleton, the CacheManager is supposed to
     * be thread safe.
     * https://github.com/ehcache/ehcache-jcache/issues/41
     */
    @Override
    public CacheManager get() {
        CachingProvider cachingProvider = Caching.getCachingProvider();
        CacheManager cacheManager = cachingProvider.getCacheManager();
        return cacheManager;
    }

}
