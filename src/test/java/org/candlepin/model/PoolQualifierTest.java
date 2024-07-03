/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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
package org.candlepin.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PoolQualifierTest {

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testAddIdWithInvalidId(String invalidId) {
        String id = TestUtil.randomString();

        PoolQualifier qualifier = new PoolQualifier()
            .addId(id)
            .addId(invalidId);

        assertThat(qualifier.getIds())
            .isNotNull()
            .singleElement()
            .isEqualTo(id);
    }

    @Test
    public void testAddId() {
        String id1 = TestUtil.randomString();
        String id2 = TestUtil.randomString();

        PoolQualifier qualifier = new PoolQualifier()
            .addId(id1)
            .addId(id2);

        assertThat(qualifier.getIds())
            .isNotNull()
            .hasSize(2)
            .containsExactlyInAnyOrder(id1, id2);
    }

    @Test
    public void testAddIdsWithNullCollection() {
        PoolQualifier qualifier = new PoolQualifier()
            .addIds(null);

        assertThat(qualifier.getIds())
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testAddIdsWithEmptyCollection() {
        PoolQualifier qualifier = new PoolQualifier()
            .addIds(new ArrayList<>());

        assertThat(qualifier.getIds())
            .isNotNull()
            .isEmpty();
    }
    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testAddIdsWithNullAndEmptyCollectionEntry(String entry) {
        String id1 = TestUtil.randomString();
        String id2 = TestUtil.randomString();
        List<String> ids = new ArrayList<>();
        ids.add(id1);
        ids.add(id2);

        PoolQualifier qualifier = new PoolQualifier()
            .addIds(ids);

        assertThat(qualifier.getIds())
            .hasSize(2)
            .containsExactlyInAnyOrder(id1, id2);

        String id3 = TestUtil.randomString();
        List<String> ids2 = new ArrayList<>();
        ids2.add(entry);
        ids2.add(id3);

        qualifier.addIds(ids2);

        assertThat(qualifier.getIds())
            .hasSize(3)
            .containsExactlyInAnyOrder(id1, id2, id3);
    }

    @Test
    public void testAddIds() {
        String id1 = TestUtil.randomString();
        String id2 = TestUtil.randomString();
        PoolQualifier qualifier = new PoolQualifier()
            .addIds(List.of(id1, id2));

        assertThat(qualifier.getIds())
            .hasSize(2)
            .containsExactlyInAnyOrder(id1, id2);

        String id3 = TestUtil.randomString();
        String id4 = TestUtil.randomString();
        qualifier.addIds(List.of(id3, id4));

        assertThat(qualifier.getIds())
            .hasSize(4)
            .containsExactlyInAnyOrder(id1, id2, id3, id4);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testRemoveIdWithInvalidId(String invalidId) {
        String id1 = TestUtil.randomString();
        String id2 = TestUtil.randomString();
        PoolQualifier qualifier = new PoolQualifier()
            .addIds(List.of(id1, id2));

        qualifier.removeId(invalidId);

        assertThat(qualifier.getIds())
            .hasSize(2)
            .containsExactlyInAnyOrder(id1, id2);
    }

    @Test
    public void testRemoveId() {
        String id1 = TestUtil.randomString();
        String id2 = TestUtil.randomString();
        PoolQualifier qualifier = new PoolQualifier()
            .addIds(List.of(id1, id2));

        qualifier.removeId(id1);

        assertThat(qualifier.getIds())
            .singleElement()
            .isEqualTo(id2);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testAddProductIdWithInvalidProductId(String invalidProductId) {
        String productId = TestUtil.randomString();

        PoolQualifier qualifier = new PoolQualifier()
            .addProductId(productId)
            .addProductId(invalidProductId);

        assertThat(qualifier.getProductIds())
            .isNotNull()
            .singleElement()
            .isEqualTo(productId);
    }

    @Test
    public void testAddProductId() {
        String productId1 = TestUtil.randomString();
        String productId2 = TestUtil.randomString();

        PoolQualifier qualifier = new PoolQualifier()
            .addProductId(productId1)
            .addProductId(productId2);

        assertThat(qualifier.getProductIds())
            .isNotNull()
            .hasSize(2)
            .containsExactlyInAnyOrder(productId1, productId2);
    }

    @Test
    public void testAddProductIdsWithNullCollection() {
        PoolQualifier qualifier = new PoolQualifier()
            .addProductIds(null);

        assertThat(qualifier.getProductIds())
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testAddProductIdsWithEmptyCollection() {
        PoolQualifier qualifier = new PoolQualifier()
            .addProductIds(new ArrayList<>());

        assertThat(qualifier.getProductIds())
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testAddProductIdsWithNullCollectionEntry() {
        String productId1 = TestUtil.randomString();
        String productId2 = TestUtil.randomString();
        List<String> productIds = new ArrayList<>();
        productIds.add(productId1);
        productIds.add(productId2);

        PoolQualifier qualifier = new PoolQualifier()
            .addProductIds(productIds);

        assertThat(qualifier.getProductIds())
            .hasSize(2)
            .containsExactlyInAnyOrder(productId1, productId2);

        String productId3 = null;
        String productId4 = TestUtil.randomString();
        List<String> productIds2 = new ArrayList<>();
        productIds2.add(productId3);
        productIds2.add(productId4);

        qualifier.addProductIds(productIds2);

        assertThat(qualifier.getProductIds())
            .hasSize(3)
            .containsExactlyInAnyOrder(productId1, productId2, productId4);
    }

    @Test
    public void testAddProductIds() {
        String productId1 = TestUtil.randomString();
        String productId2 = TestUtil.randomString();
        PoolQualifier qualifier = new PoolQualifier()
            .addProductIds(List.of(productId1, productId2));

        assertThat(qualifier.getProductIds())
            .hasSize(2)
            .containsExactlyInAnyOrder(productId1, productId2);

        String productId3 = TestUtil.randomString();
        String productId4 = TestUtil.randomString();
        qualifier.addProductIds(List.of(productId3, productId4));

        assertThat(qualifier.getProductIds())
            .hasSize(4)
            .containsExactlyInAnyOrder(productId1, productId2, productId3, productId4);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testRemoveProductIdWithInvalidProductId(String invalidProductId) {
        String productId1 = TestUtil.randomString();
        String productId2 = TestUtil.randomString();
        PoolQualifier qualifier = new PoolQualifier()
            .addProductIds(List.of(productId1, productId2));

        qualifier.removeProductId(invalidProductId);

        assertThat(qualifier.getProductIds())
            .hasSize(2)
            .containsExactlyInAnyOrder(productId1, productId2);
    }

    @Test
    public void testRemoveProductId() {
        String productId1 = TestUtil.randomString();
        String productId2 = TestUtil.randomString();
        PoolQualifier qualifier = new PoolQualifier()
            .addProductIds(List.of(productId1, productId2));

        qualifier.removeProductId(productId1);

        assertThat(qualifier.getProductIds())
            .singleElement()
            .isEqualTo(productId2);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testAddMatchWithInvalidMatch(String invalidMatch) {
        String match = TestUtil.randomString();

        PoolQualifier qualifier = new PoolQualifier()
            .addMatch(match)
            .addMatch(invalidMatch);

        assertThat(qualifier.getMatches())
            .isNotNull()
            .singleElement()
            .isEqualTo(match);
    }

    @Test
    public void testAddMatch() {
        String match1 = TestUtil.randomString();
        String match2 = TestUtil.randomString();

        PoolQualifier qualifier = new PoolQualifier()
            .addMatch(match1)
            .addMatch(match2);

        assertThat(qualifier.getMatches())
            .isNotNull()
            .hasSize(2)
            .containsExactlyInAnyOrder(match1, match2);
    }

    @Test
    public void testAddMatchesWithNullCollection() {
        PoolQualifier qualifier = new PoolQualifier()
            .addMatches(null);

        assertThat(qualifier.getMatches())
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testAddMatchesWithEmptyCollection() {
        PoolQualifier qualifier = new PoolQualifier()
            .addMatches(new ArrayList<>());

        assertThat(qualifier.getMatches())
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testAddMatchesWithNullCollectionEntry() {
        String match1 = TestUtil.randomString();
        String match2 = TestUtil.randomString();
        List<String> matches = new ArrayList<>();
        matches.add(match1);
        matches.add(match2);

        PoolQualifier qualifier = new PoolQualifier()
            .addMatches(matches);

        assertThat(qualifier.getMatches())
            .hasSize(2)
            .containsExactlyInAnyOrder(match1, match2);

        String match3 = null;
        String match4 = TestUtil.randomString();
        List<String> matches2 = new ArrayList<>();
        matches2.add(match3);
        matches2.add(match4);

        qualifier.addMatches(matches2);

        assertThat(qualifier.getMatches())
            .hasSize(3)
            .containsExactlyInAnyOrder(match1, match2, match4);
    }

    @Test
    public void testAddMatches() {
        String match1 = TestUtil.randomString();
        String match2 = TestUtil.randomString();
        PoolQualifier qualifier = new PoolQualifier()
            .addMatches(List.of(match1, match2));

        assertThat(qualifier.getMatches())
            .hasSize(2)
            .containsExactlyInAnyOrder(match1, match2);

        String match3 = TestUtil.randomString();
        String match4 = TestUtil.randomString();
        qualifier.addMatches(List.of(match3, match4));

        assertThat(qualifier.getMatches())
            .hasSize(4)
            .containsExactlyInAnyOrder(match1, match2, match3, match4);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testRemoveMatchWithInvalidMatch(String invalidMatch) {
        String match1 = TestUtil.randomString();
        String match2 = TestUtil.randomString();
        PoolQualifier qualifier = new PoolQualifier()
            .addMatches(List.of(match1, match2));

        qualifier.removeMatch(invalidMatch);

        assertThat(qualifier.getMatches())
            .hasSize(2)
            .containsExactlyInAnyOrder(match1, match2);
    }

    @Test
    public void testRemoveMatch() {
        String match1 = TestUtil.randomString();
        String match2 = TestUtil.randomString();
        PoolQualifier qualifier = new PoolQualifier()
            .addMatches(List.of(match1, match2));

        qualifier.removeMatch(match1);

        assertThat(qualifier.getMatches())
            .singleElement()
            .isEqualTo(match2);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testAddAttributeWithInvalidKey(String key) {
        PoolQualifier qualifier = new PoolQualifier()
            .addAttribute(key, TestUtil.randomString());

        assertThat(qualifier.getAttributes())
            .isEmpty();
    }

    @Test
    public void testAddAttribute() {
        String attKey1 = TestUtil.randomString();
        String attVal1 = TestUtil.randomString();

        String attKey2 = TestUtil.randomString();
        String attVal2 = TestUtil.randomString();
        String attVal3 = TestUtil.randomString();

        PoolQualifier qualifier = new PoolQualifier()
            .addAttribute(attKey1, attVal1)
            .addAttribute(attKey2, attVal2)
            .addAttribute(attKey2, attVal3);

        assertThat(qualifier.getAttributes())
            .hasSize(2)
            .containsEntry(attKey1, List.of(attVal1))
            .containsEntry(attKey2, List.of(attVal2, attVal3));

        String attVal4 = TestUtil.randomString();
        qualifier.addAttribute(attKey1, attVal4);

        assertThat(qualifier.getAttributes())
            .hasSize(2)
            .containsEntry(attKey1, List.of(attVal1, attVal4))
            .containsEntry(attKey2, List.of(attVal2, attVal3));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testRemoveAttributeWithInvalidKey(String key) {
        String attKey1 = TestUtil.randomString();
        String attVal1 = TestUtil.randomString();

        PoolQualifier qualifier = new PoolQualifier()
            .addAttribute(attKey1, attVal1);

        qualifier.removeAttribute(key);

        assertThat(qualifier.getAttributes())
            .hasSize(1)
            .containsEntry(attKey1, List.of(attVal1));
    }

    @Test
    public void testRemoveAttribute() {
        String attKey1 = TestUtil.randomString();
        String attVal1 = TestUtil.randomString();

        String attKey2 = TestUtil.randomString();
        String attVal2 = TestUtil.randomString();
        String attVal3 = TestUtil.randomString();

        PoolQualifier qualifier = new PoolQualifier()
            .addAttribute(attKey1, attVal1)
            .addAttribute(attKey2, attVal2)
            .addAttribute(attKey2, attVal3);

        assertThat(qualifier.getAttributes())
            .hasSize(2)
            .containsEntry(attKey1, List.of(attVal1))
            .containsEntry(attKey2, List.of(attVal2, attVal3));

        qualifier.removeAttribute(attKey2);

        assertThat(qualifier.getAttributes())
            .hasSize(1)
            .containsEntry(attKey1, List.of(attVal1));
    }

    @Test
    public void testSetActiveOn() {
        Date activeOn = new Date();

        PoolQualifier qualifier = new PoolQualifier()
            .setActiveOn(activeOn);

        assertThat(qualifier.getActiveOn())
            .isEqualTo(activeOn);
    }

    @Test
    public void testSetAddFuture() {
        PoolQualifier qualifier = new PoolQualifier()
            .setAddFuture(true);

        assertThat(qualifier.getAddFuture())
            .isTrue();
    }

    @Test
    public void testSetOnlyFuture() {
        PoolQualifier qualifier = new PoolQualifier()
            .setOnlyFuture(true);

        assertThat(qualifier.isOnlyFuture())
            .isTrue();
    }

    @Test
    public void testSetIncludeWarnings() {
        PoolQualifier qualifier = new PoolQualifier()
            .setIncludeWarnings(true);

        assertTrue(qualifier.includeWarnings());
    }

    @Test
    public void testSetAfter() {
        Date after = new Date();

        PoolQualifier qualifier = new PoolQualifier()
            .setAfter(after);

        assertThat(qualifier.getAfter())
            .isNotNull()
            .isEqualTo(after);
    }

    @Test
    public void testSetConsumer() {
        Consumer consumer = TestUtil.createConsumer();

        PoolQualifier qualifier = new PoolQualifier()
            .setConsumer(consumer);

        assertThat(qualifier.getConsumer())
            .isNotNull()
            .isEqualTo(consumer);
    }

    @Test
    public void testSetOwnerId() {
        String ownerId = TestUtil.randomString();

        PoolQualifier qualifier = new PoolQualifier()
            .setOwnerId(ownerId);

        assertThat(qualifier.getOwnerId())
            .isNotNull()
            .isEqualTo(ownerId);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testAddSubscriptionIdWithInvalidId(String invalidIdSubscriptionId) {
        String id = TestUtil.randomString();

        PoolQualifier qualifier = new PoolQualifier()
            .addSubscriptionId(id)
            .addSubscriptionId(invalidIdSubscriptionId);

        assertThat(qualifier.getSubscriptionIds())
            .isNotNull()
            .singleElement()
            .isEqualTo(id);
    }

    @Test
    public void testAddSubscriptionId() {
        String subscriptionId1 = TestUtil.randomString();
        String subscriptionId2 = TestUtil.randomString();

        PoolQualifier qualifier = new PoolQualifier()
            .addSubscriptionId(subscriptionId1)
            .addSubscriptionId(subscriptionId2);

        assertThat(qualifier.getSubscriptionIds())
            .isNotNull()
            .hasSize(2)
            .containsExactlyInAnyOrder(subscriptionId1, subscriptionId2);
    }

    @Test
    public void testAddSubscriptionIdsWithNullCollection() {
        PoolQualifier qualifier = new PoolQualifier()
            .addSubscriptionIds(null);

        assertThat(qualifier.getSubscriptionIds())
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testAddSubscriptionIdsWithEmptyCollection() {
        PoolQualifier qualifier = new PoolQualifier()
            .addSubscriptionIds(new ArrayList<>());

        assertThat(qualifier.getSubscriptionIds())
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testAddSubscriptionIdsWithNullCollectionEntry() {
        String subscriptionId1 = TestUtil.randomString();
        String subscriptionId2 = TestUtil.randomString();
        List<String> subIds = new ArrayList<>();
        subIds.add(subscriptionId1);
        subIds.add(subscriptionId2);

        PoolQualifier qualifier = new PoolQualifier()
            .addSubscriptionIds(subIds);

        assertThat(qualifier.getSubscriptionIds())
            .hasSize(2)
            .containsExactlyInAnyOrder(subscriptionId1, subscriptionId2);

        String subscriptionId3 = null;
        String subscriptionId4 = TestUtil.randomString();
        List<String> subIds2 = new ArrayList<>();
        subIds2.add(subscriptionId3);
        subIds2.add(subscriptionId4);

        qualifier.addSubscriptionIds(subIds2);

        assertThat(qualifier.getSubscriptionIds())
            .hasSize(3)
            .containsExactlyInAnyOrder(subscriptionId1, subscriptionId2, subscriptionId4);
    }

    @Test
    public void testAddSubscriptionIds() {
        String subscriptionId1 = TestUtil.randomString();
        String subscriptionId2 = TestUtil.randomString();
        PoolQualifier qualifier = new PoolQualifier()
            .addSubscriptionIds(List.of(subscriptionId1, subscriptionId2));

        assertThat(qualifier.getSubscriptionIds())
            .hasSize(2)
            .containsExactlyInAnyOrder(subscriptionId1, subscriptionId2);

        String subscriptionId3 = TestUtil.randomString();
        String subscriptionId4 = TestUtil.randomString();
        qualifier.addSubscriptionIds(List.of(subscriptionId3, subscriptionId4));

        assertThat(qualifier.getSubscriptionIds())
            .hasSize(4)
            .containsExactlyInAnyOrder(subscriptionId1, subscriptionId2, subscriptionId3, subscriptionId4);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testRemoveSubscriptionIdWithInvalidId(String invalidIdSubscriptionId) {
        String subscriptionId1 = TestUtil.randomString();
        String subscriptionId2 = TestUtil.randomString();
        PoolQualifier qualifier = new PoolQualifier()
            .addSubscriptionIds(List.of(subscriptionId1, subscriptionId2));

        qualifier.removeSubscriptionId(invalidIdSubscriptionId);

        assertThat(qualifier.getSubscriptionIds())
            .hasSize(2)
            .containsExactlyInAnyOrder(subscriptionId1, subscriptionId2);
    }

    @Test
    public void testRemoveSubscriptionId() {
        String subscriptionId1 = TestUtil.randomString();
        String subscriptionId2 = TestUtil.randomString();
        PoolQualifier qualifier = new PoolQualifier()
            .addSubscriptionIds(List.of(subscriptionId1, subscriptionId2));

        qualifier.removeSubscriptionId(subscriptionId1);

        assertThat(qualifier.getSubscriptionIds())
            .singleElement()
            .isEqualTo(subscriptionId2);
    }

    @Test
    public void testSetActivationKey() {
        ActivationKey key = new ActivationKey()
            .setId(TestUtil.randomString())
            .setName(TestUtil.randomString());

        PoolQualifier qualifier = new PoolQualifier()
            .setActivationKey(key);

        assertThat(qualifier.getActivationKey())
            .isEqualTo(key);
    }

}
