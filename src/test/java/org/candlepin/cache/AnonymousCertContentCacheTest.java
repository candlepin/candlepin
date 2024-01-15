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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.ConfigurationException;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.model.dto.Content;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

@ExtendWith(MockitoExtension.class)
public class AnonymousCertContentCacheTest {

    private DevConfig config;

    @BeforeEach
    public void beforeEach() {
        config = TestConfig.defaults();
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(longs = { 0L, -1000L })
    public void testCacheCreationWithInvalidDuration(long duration) throws Exception {
        config.setProperty(ConfigProperties.CACHE_ANON_CERT_CONTENT_TTL, String.valueOf(duration));

        assertThrows(ConfigurationException.class, () -> {
            new AnonymousCertContentCache(config);
        });
    }

    @Test
    public void testCacheCreationWithInvalidMaxEntriesConfig() throws Exception {
        config.setProperty(ConfigProperties.CACHE_ANON_CERT_CONTENT_MAX_ENTRIES, "-100");

        assertThrows(ConfigurationException.class, () -> {
            new AnonymousCertContentCache(config);
        });
    }

    @Test
    public void testGetWithInvalidSkuIds() throws Exception {
        AnonymousCertContentCache cache = new AnonymousCertContentCache(config);

        assertThrows(IllegalArgumentException.class, () -> {
            cache.get(null);
        });
    }

    @Test
    public void testGetWithEmptySkuIds() throws Exception {
        AnonymousCertContentCache cache = new AnonymousCertContentCache(config);
        cache.put(List.of(TestUtil.randomString()), getRandomCertContent(2));

        AnonymousCertContent actual = cache.get(List.of());

        assertNull(actual);
    }

    @Test
    public void testGetWithContentInCache() throws Exception {
        AnonymousCertContentCache cache = new AnonymousCertContentCache(config);

        List<String> skuIds = List.of(TestUtil.randomString(), TestUtil.randomString());
        AnonymousCertContent expected = getRandomCertContent(2);
        cache.put(skuIds, expected);

        AnonymousCertContent actual = cache.get(skuIds);

        assertThat(actual)
            .isNotNull()
            .isEqualTo(expected);
    }

    @Test
    public void testGetWithDuplicateSkus() throws Exception {
        AnonymousCertContentCache cache = new AnonymousCertContentCache(config);

        String skuId1 = TestUtil.randomString();
        List<String> skuIds = new ArrayList<>();
        skuIds.add(skuId1);
        skuIds.add(TestUtil.randomString());
        AnonymousCertContent expected = getRandomCertContent(2);
        cache.put(skuIds, expected);
        // Add duplicate entry. The duplicate should be filtered when generating the cache key
        skuIds.add(skuId1);

        AnonymousCertContent actual = cache.get(skuIds);

        assertThat(actual)
            .isNotNull()
            .isEqualTo(expected);
    }

    @Test
    public void testGetWithContentNotInCache() throws Exception {
        AnonymousCertContentCache cache = new AnonymousCertContentCache(config);

        AnonymousCertContent expected = getRandomCertContent(2);
        cache.put(List.of(TestUtil.randomString(), TestUtil.randomString()), expected);

        AnonymousCertContent actual = cache.get(List.of(TestUtil.randomString()));

        assertNull(actual);
    }

    @Test
    public void testGetWithContentInvalidatedByExpirationDuration() throws Exception {
        long expirationDuration = 2000L;
        config.setProperty(ConfigProperties.CACHE_ANON_CERT_CONTENT_TTL, String.valueOf(expirationDuration));
        AnonymousCertContentCache cache = new AnonymousCertContentCache(config);
        List<String> skuIds = List.of(TestUtil.randomString(), TestUtil.randomString());
        AnonymousCertContent expected = getRandomCertContent(2);
        cache.put(skuIds, expected);

        // Wait for cache entry to be invalidated
        Thread.sleep(expirationDuration + 100L);
        AnonymousCertContent actual = cache.get(skuIds);

        assertNull(actual);
    }

    @Test
    public void testPutWithInvalidSkuIds() throws Exception {
        AnonymousCertContentCache cache = new AnonymousCertContentCache(config);
        AnonymousCertContent content = getRandomCertContent(2);

        assertThrows(IllegalArgumentException.class, () -> {
            cache.put(null, content);
        });
    }

    @Test
    public void testPutWithInvalidContent() throws Exception {
        AnonymousCertContentCache cache = new AnonymousCertContentCache(config);
        List<String> skuIds = List.of(TestUtil.randomString());

        assertThrows(IllegalArgumentException.class, () -> {
            cache.put(skuIds, null);
        });
    }

    @Test
    public void testRemoveWithInvalidSkuIds() throws Exception {
        AnonymousCertContentCache cache = new AnonymousCertContentCache(config);

        assertThrows(IllegalArgumentException.class, () -> {
            cache.remove(null);
        });
    }

    @Test
    public void testRemoveWithContentInCache() throws Exception {
        AnonymousCertContentCache cache = new AnonymousCertContentCache(config);

        List<String> skuIds = List.of(TestUtil.randomString(), TestUtil.randomString());
        cache.put(skuIds, getRandomCertContent(2));
        cache.put(List.of(TestUtil.randomString()), getRandomCertContent(2));

        cache.remove(skuIds);

        assertNull(cache.get(skuIds));
    }

    @Test
    public void testRemoveAll() throws Exception {
        AnonymousCertContentCache cache = new AnonymousCertContentCache(config);

        List<String> skuIds1 = List.of(TestUtil.randomString(), TestUtil.randomString());
        List<String> skuIds2 = List.of(TestUtil.randomString(), TestUtil.randomString());
        List<String> skuIds3 = List.of(TestUtil.randomString(), TestUtil.randomString());
        List<String> skuIds4 = List.of(TestUtil.randomString(), TestUtil.randomString());
        cache.put(skuIds1, getRandomCertContent(2));
        cache.put(skuIds2, getRandomCertContent(4));
        cache.put(skuIds3, getRandomCertContent(6));
        cache.put(skuIds4, getRandomCertContent(8));

        cache.removeAll();

        assertNull(cache.get(skuIds1));
        assertNull(cache.get(skuIds2));
        assertNull(cache.get(skuIds3));
        assertNull(cache.get(skuIds4));
    }

    private AnonymousCertContent getRandomCertContent(int size) {
        return new AnonymousCertContent(TestUtil.randomString(), getRandomContents(size));
    }

    private List<Content> getRandomContents(int size) {
        List<Content> content = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            content.add(getRandomContent());
        }

        return content;
    }

    private Content getRandomContent() {
        Content content = new Content();
        content.setId(TestUtil.randomString());
        content.setLabel(TestUtil.randomString());
        content.setPath(TestUtil.randomString());
        content.setType(TestUtil.randomString());
        content.setVendor(TestUtil.randomString());

        return content;
    }

}
