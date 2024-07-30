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
package org.candlepin.auth.permissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.model.AnonymousCloudConsumer;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;

import java.util.List;



public class AnonymousCloudConsumerPermissionTest {

    @Test
    public void testAnonymousCloudConsumerWithNullAnonymousCloudConsumer() {
        assertThrows(NullPointerException.class, () -> new AnonymousCloudConsumerPermission(null));
    }

    @Test
    public void testGetOwner() {
        AnonymousCloudConsumerPermission permission = new AnonymousCloudConsumerPermission(
            new AnonymousCloudConsumer());

        assertNull(permission.getOwner());
    }

    @Test
    public void testGetTargetType() {
        AnonymousCloudConsumerPermission permission = new AnonymousCloudConsumerPermission(
            new AnonymousCloudConsumer());

        assertEquals(AnonymousCloudConsumer.class, permission.getTargetType());
    }

    @Test
    public void testCanAccessTargetWithSameAnonymousCloudConsumer() {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setId("id")
            .setUuid("uuid")
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setProductIds(List.of("productId"))
            .setCloudProviderShortName(TestUtil.randomString());

        AnonymousCloudConsumerPermission permission = new AnonymousCloudConsumerPermission(consumer);

        assertTrue(permission.canAccessTarget(consumer, null, null));
    }

    @Test
    public void testCanAccessTargetWithOtherAnonymousCloudConsumer() {
        AnonymousCloudConsumer consumer1 = new AnonymousCloudConsumer()
            .setId("id")
            .setUuid("uuid")
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setProductIds(List.of("productId"))
            .setCloudProviderShortName(TestUtil.randomString());

        AnonymousCloudConsumer consumer2 = new AnonymousCloudConsumer()
            .setId("id2")
            .setUuid("uuid2")
            .setCloudAccountId("cloudAccountId2")
            .setCloudInstanceId("instanceId2")
            .setProductIds(List.of("productId"))
            .setCloudProviderShortName(TestUtil.randomString());

        AnonymousCloudConsumerPermission permission = new AnonymousCloudConsumerPermission(consumer1);

        assertFalse(permission.canAccessTarget(consumer2, null, null));
    }

    @Test
    public void testGetAnonymousCloudConsumer() {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setId("id")
            .setUuid("uuid")
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setProductIds(List.of("productId"))
            .setCloudProviderShortName(TestUtil.randomString());

        AnonymousCloudConsumerPermission permission = new AnonymousCloudConsumerPermission(consumer);

        assertThat(permission)
            .returns(consumer, AnonymousCloudConsumerPermission::getAnonymousCloudConsumer);
    }

}
