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

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.config.ConfigurationException;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.Collection;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A thread safe cache for consumer certificate content. Entries are evicted based on a time-to-live eviction
 * policy.
 */
@Singleton
public class AnonymousCertContentCache {
    private Cache<MultiKey, AnonymousCertContent> cache;

    @Inject
    public AnonymousCertContentCache(Configuration config) throws ConfigurationException {
        Objects.requireNonNull(config);

        long expirationDuration = config.getLong(ConfigProperties.CACHE_ANON_CERT_CONTENT_TTL);
        if (expirationDuration <= 0) {
            String msg = ConfigProperties.CACHE_ANON_CERT_CONTENT_TTL + " value must be larger than 0";
            throw new ConfigurationException(msg);
        }

        long maxEntries = config.getLong(ConfigProperties.CACHE_ANON_CERT_CONTENT_MAX_ENTRIES);
        if (maxEntries < 0) {
            String msg = ConfigProperties.CACHE_ANON_CERT_CONTENT_MAX_ENTRIES +
                " must be larger than or equal to 0";
            throw new ConfigurationException(msg);
        }

        cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMillis(expirationDuration))
            .maximumSize(maxEntries)
            .build();
    }

    /**
     * Retrieves cached {@link AnonymousCertContent} for the provided top level SKU IDs.
     *
     * @param skuIds
     *  the top level SKU IDs to retrieve consumer certificate content for
     *
     * @throws IllegalArgumentException
     *  if the provided SKU IDs are null
     *
     * @return
     *  the cached consumer certificate content for the provided top level SKU IDs
     */
    public AnonymousCertContent get(Collection<String> skuIds) {
        if (skuIds == null) {
            throw new IllegalArgumentException("sku ID is null");
        }

        if (skuIds.isEmpty()) {
            return null;
        }

        return cache.getIfPresent(new MultiKey(skuIds));
    }

    /**
     * Inserts {@link AnonymousCertContent} into the cache for the provided top level SKU IDs. An existing
     * entry in the cache for the same SKU IDs will be replaced.
     *
     * @param skuIds
     *  the top level SKU IDs to associate the consumer certificate content to in the cache
     *
     * @param content
     *  the consumer certificate content to insert into the cache
     *
     * @throws IllegalArgumentException
     *  if the provided SKU IDs are null or the provided consumer certificate content is null
     */
    public void put(Collection<String> skuIds, AnonymousCertContent content) {
        if (skuIds == null) {
            throw new IllegalArgumentException("sku ID is null");
        }

        if (content == null) {
            throw new IllegalArgumentException("content is null");
        }

        cache.put(new MultiKey(skuIds), content);
    }

    /**
     * Removes the cached {@link AnonymousCertContent} associated to the provided top level SKU IDs.
     *
     * @param skuIds
     *  the top level SKU IDs to remove cached consumer certificate content for
     *
     * @throws IllegalArgumentException
     *  if the provided SKU IDs are null
     */
    public void remove(Collection<String> skuIds) {
        if (skuIds == null) {
            throw new IllegalArgumentException("sku IDs is null");
        }

        cache.invalidate(new MultiKey(skuIds));
    }

    /**
     * Clears all entries in the cache
     */
    public void removeAll() {
        cache.invalidateAll();
    }

}
