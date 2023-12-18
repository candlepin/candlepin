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
package org.candlepin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.auth.permissions.AnonymousCloudConsumerPermission;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.model.AnonymousCloudConsumer;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;

import java.util.List;



public class AnonymousCloudConsumerPrincipalTest {

    @Test
    public void testAnonymousCloudConsumerPrincipalTestConstructorWithNullConsumer() {
        assertThrows(NullPointerException.class, () -> new AnonymousCloudConsumerPrincipal(null));
    }

    @Test
    public void testAnonymousCloudConsumerPrincipalPermissions() {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setId("id")
            .setUuid("uuid")
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setProductIds(List.of("productId"))
            .setCloudProviderShortName(TestUtil.randomString());

        AnonymousCloudConsumerPrincipal principal = new AnonymousCloudConsumerPrincipal(consumer);

        assertThat(principal.permissions)
            .singleElement()
            .returns(AnonymousCloudConsumerPermission.class, Permission::getClass);
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

        AnonymousCloudConsumerPrincipal principal = new AnonymousCloudConsumerPrincipal(consumer);

        assertThat(principal)
            .returns(consumer, AnonymousCloudConsumerPrincipal::getAnonymousCloudConsumer);
    }

    @Test
    public void testGetType() {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setId("id")
            .setUuid("uuid")
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setProductIds(List.of("productId"))
            .setCloudProviderShortName(TestUtil.randomString());

        AnonymousCloudConsumerPrincipal principal = new AnonymousCloudConsumerPrincipal(consumer);

        assertThat(principal)
            .returns("anonymouscloudconsumer", AnonymousCloudConsumerPrincipal::getType);
    }

    @Test
    public void testHasFullAccess() {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setId("id")
            .setUuid("uuid")
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setProductIds(List.of("productId"))
            .setCloudProviderShortName(TestUtil.randomString());

        AnonymousCloudConsumerPrincipal principal = new AnonymousCloudConsumerPrincipal(consumer);

        assertFalse(principal.hasFullAccess());
    }

    @Test
    public void testGetName() {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setId("id")
            .setUuid("uuid")
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setProductIds(List.of("productId"))
            .setCloudProviderShortName(TestUtil.randomString());

        AnonymousCloudConsumerPrincipal principal = new AnonymousCloudConsumerPrincipal(consumer);

        assertThat(principal)
            .returns(consumer.getUuid(), AnonymousCloudConsumerPrincipal::getName);
    }

    @Test
    public void testEqualsWithSelf() {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setId("id")
            .setUuid("uuid")
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setProductIds(List.of("productId"))
            .setCloudProviderShortName(TestUtil.randomString());

        AnonymousCloudConsumerPrincipal principal = new AnonymousCloudConsumerPrincipal(consumer);

        assertEquals(principal, principal);
    }

    @Test
    public void testEqualsWithNull() {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setId("id")
            .setUuid("uuid")
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setProductIds(List.of("productId"))
            .setCloudProviderShortName(TestUtil.randomString());

        AnonymousCloudConsumerPrincipal principal = new AnonymousCloudConsumerPrincipal(consumer);

        assertNotEquals(null, principal);
    }

    @Test
    public void testEqualsWithOtherClass() {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setId("id")
            .setUuid("uuid")
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setProductIds(List.of("productId"))
            .setCloudProviderShortName(TestUtil.randomString());

        AnonymousCloudConsumerPrincipal principal = new AnonymousCloudConsumerPrincipal(consumer);

        assertNotEquals(List.of(), principal);
    }

    @Test
    public void testEqualsWithOtherUuid() {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setId("id")
            .setUuid("uuid")
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setProductIds(List.of("productId"))
            .setCloudProviderShortName(TestUtil.randomString());

        AnonymousCloudConsumerPrincipal principal = new AnonymousCloudConsumerPrincipal(consumer);

        assertNotEquals("unknown-uuid", principal);
    }

    @Test
    public void testEqualsWithSameAnonymousCloudConsumer() {
        AnonymousCloudConsumer consumer1 = new AnonymousCloudConsumer()
            .setId("id")
            .setUuid("uuid")
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setProductIds(List.of("productId"))
            .setCloudProviderShortName(TestUtil.randomString());

        AnonymousCloudConsumer consumer2 = new AnonymousCloudConsumer()
            .setId("id")
            .setUuid("uuid")
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setProductIds(List.of("productId"))
            .setCloudProviderShortName(TestUtil.randomString());

        AnonymousCloudConsumerPrincipal principal1 = new AnonymousCloudConsumerPrincipal(consumer1);
        AnonymousCloudConsumerPrincipal principal2 = new AnonymousCloudConsumerPrincipal(consumer2);

        assertEquals(principal1, principal2);
    }

}
