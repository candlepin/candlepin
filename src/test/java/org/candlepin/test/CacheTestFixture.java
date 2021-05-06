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
package org.candlepin.test;

import org.candlepin.cache.CandlepinCache;
import org.candlepin.cache.JCacheManagerProvider;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Singleton;

import javax.cache.CacheManager;

/**
 * Test that runs in-memory Cache and in-memory database
 * @author fnguyen
 *
 */
public class CacheTestFixture extends DatabaseTestFixture {

    @Override
    protected Module getGuiceOverrideModule() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                /**
                 * Standard test uses mocked CandlepinCache. In this CacheTestFixture we gonna override it
                 * back to the standard cache implementation.
                 */
                bind(CandlepinCache.class);
                bind(CacheManager.class).toProvider(JCacheManagerProvider.class).in(Singleton.class);
            }
        };
    }

    @Override
    public void shutdown() {
        super.shutdown();

        CacheManager cacheManager = injector.getInstance(CacheManager.class);
        cacheManager.close();

    }
}
