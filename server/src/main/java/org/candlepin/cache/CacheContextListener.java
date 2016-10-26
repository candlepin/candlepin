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

import com.google.inject.Injector;

import java.util.concurrent.TimeUnit;

import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;

/**
 * Configuration of caches in candlepin. This is configuration according to JCache API.
 * Additional, EHCache specific, configurations are in ehcache.xml and ehcache-stats.xml
 * configuration.
 * @author fnguyen
 *
 */
public class CacheContextListener {
    public static final String CACHE_STATUS = "statuscache";
    public static final String CACHE_PRODUCT_FULL = "productfullcache";


    public void contextInitialized(Injector injector) {
        CacheManager cacheManager = injector.getInstance(CacheManager.class);

        MutableConfiguration<String, Status> config = new MutableConfiguration<String, Status>();
        config.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(new Duration(TimeUnit.SECONDS, 5)));
        config.setTypes(String.class, Status.class);
        config.setStoreByValue(false);

        cacheManager.createCache(CACHE_STATUS, config);
    }
}
