/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;

import java.util.Date;

public class ContentAccessPayloadTest {

    @Test
    public void testSetIdWithNullValue() {
        ContentAccessPayload payload = new ContentAccessPayload();

        assertThrows(IllegalArgumentException.class, () -> {
            payload.setId(null);
        });
    }

    @Test
    public void testSetId() {
        ContentAccessPayload payload = new ContentAccessPayload();
        String expectedId = TestUtil.randomString("id-");

        payload.setId(expectedId);

        assertThat(payload.getId())
            .isNotNull()
            .isEqualTo(expectedId);
    }

    @Test
    public void testSetOwnerWithNullValue() {
        ContentAccessPayload payload = new ContentAccessPayload();

        assertThrows(IllegalArgumentException.class, () -> {
            payload.setOwner(null);
        });
    }

    @Test
    public void testSetOwnerWithNullOwnerId() {
        Owner owner = new Owner();
        ContentAccessPayload payload = new ContentAccessPayload();

        assertThrows(IllegalArgumentException.class, () -> {
            payload.setOwner(owner);
        });
    }

    @Test
    public void testSetOwner() {
        String expectedOwnerId = TestUtil.randomString("id-");
        Owner owner = new Owner()
            .setId(expectedOwnerId);

        ContentAccessPayload payload = new ContentAccessPayload()
            .setOwner(owner);

        assertThat(payload.getOwnerId())
            .isNotNull()
            .isEqualTo(expectedOwnerId);
    }

    @Test
    public void testSetOwnerIdWithNullValue() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ContentAccessPayload()
                .setOwnerId(null);
        });
    }

    @Test
    public void testSetOwnerId() {
        String expectedOwnerId = TestUtil.randomString("id-");

        ContentAccessPayload payload = new ContentAccessPayload()
            .setOwnerId(expectedOwnerId);

        assertThat(payload.getOwnerId())
            .isNotNull()
            .isEqualTo(expectedOwnerId);
    }

    @Test
    public void testSetPayloadKeyWithNullValue() {
        ContentAccessPayload payload = new ContentAccessPayload();

        assertThrows(IllegalArgumentException.class, () -> {
            payload.setPayloadKey(null);
        });
    }

    @Test
    public void testSetPayloadKey() {
        String expectedPayloadKey = TestUtil.randomString("key-");

        ContentAccessPayload payload = new ContentAccessPayload()
            .setPayloadKey(expectedPayloadKey);

        assertThat(payload.getPayloadKey())
            .isNotNull()
            .isEqualTo(expectedPayloadKey);
    }

    @Test
    public void testSetPayloadWithNullValue() {
        ContentAccessPayload payload = new ContentAccessPayload();

        assertThrows(IllegalArgumentException.class, () -> {
            payload.setPayload(null);
        });
    }

    @Test
    public void testSetPayload() {
        String expectedPayload = TestUtil.randomString("payload-");

        ContentAccessPayload payload = new ContentAccessPayload()
            .setPayload(expectedPayload);

        assertThat(payload.getPayload())
            .isNotNull()
            .isEqualTo(expectedPayload);
    }

    @Test
    public void testSetTimestampWithNullValue() {
        ContentAccessPayload payload = new ContentAccessPayload();

        assertThrows(IllegalArgumentException.class, () -> {
            payload.setTimestamp(null);
        });
    }

    @Test
    public void testSetTimestamp() {
        Date expected = new Date();

        ContentAccessPayload payload = new ContentAccessPayload()
            .setTimestamp(expected);

        assertThat(payload.getTimestamp())
            .isNotNull()
            .isEqualTo(expected);
    }

    @Test
    public void testToString() {
        ContentAccessPayload payload = new ContentAccessPayload()
            .setOwnerId(TestUtil.randomString("owner-id-"))
            .setPayloadKey(TestUtil.randomString("payload-key-"))
            .setTimestamp(new Date());

        assertThat(payload.toString())
            .isNotNull();
    }

    @Test
    public void testToStringWithNullTimestamp() {
        ContentAccessPayload payload = new ContentAccessPayload()
            .setOwnerId(TestUtil.randomString("owner-id-"))
            .setPayloadKey(TestUtil.randomString("payload-key-"));

        assertThat(payload.toString())
            .isNotNull();
    }

}

