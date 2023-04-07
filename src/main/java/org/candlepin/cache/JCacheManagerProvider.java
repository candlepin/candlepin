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
package org.candlepin.cache;

import static org.candlepin.config.ConfigProperties.CACHE_CONFIG_FILE_URI;

import org.candlepin.config.Configuration;

import com.google.inject.Provider;

import java.net.URISyntaxException;

import javax.cache.CacheException;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;
import javax.inject.Inject;

/**
 * Provides object cache by indexed by String.
 *
 * @author fnguyen
 */
public class JCacheManagerProvider implements Provider<CacheManager> {

    private Configuration config;

    @Inject
    JCacheManagerProvider(Configuration config) {
        this.config = config;
    }

    /**
     * It is safe to bind this as singleton, the CacheManager is supposed to
     * be thread safe.
     * https://github.com/ehcache/ehcache-jcache/issues/41
     */
    @Override
    public CacheManager get() {
        CacheManager cacheManager = null;

        CachingProvider cachingProvider = Caching.getCachingProvider();
        String cacheManagerUri = config.getString(CACHE_CONFIG_FILE_URI);

        try {
            ClassLoader defaultClassLoader = cachingProvider.getDefaultClassLoader();
            cacheManager = cachingProvider
                .getCacheManager(defaultClassLoader.getResource(cacheManagerUri).toURI(), defaultClassLoader);

            return cacheManager;
        }
        catch (URISyntaxException e) {
            throw new CacheException("Couldn't load URI from " + cacheManagerUri, e);
        }
    }
}
