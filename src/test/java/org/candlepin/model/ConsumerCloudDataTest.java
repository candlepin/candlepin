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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ConsumerCloudDataTest {

    @Test
    public void testSetId() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();
        String validId = "12345";
        consumerCloudData.setId(validId);
        assertEquals(validId, consumerCloudData.getId());

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            consumerCloudData.setId(null);
        });
        assertEquals("ID is null or blank", exception.getMessage());

        exception = assertThrows(IllegalArgumentException.class, () -> {
            consumerCloudData.setId("");
        });
        assertEquals("ID is null or blank", exception.getMessage());

        exception = assertThrows(IllegalArgumentException.class, () -> {
            consumerCloudData.setId("123456789012345678901234567890123");
        });
        assertEquals("ID exceeds the max length", exception.getMessage());
    }

    @Test
    public void testSetCloudProviderShortName() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();
        String validName = "aws";
        consumerCloudData.setCloudProviderShortName(validName);
        assertEquals(validName, consumerCloudData.getCloudProviderShortName());

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            consumerCloudData.setCloudProviderShortName(null);
        });
        assertEquals("cloudProviderShortName is null", exception.getMessage());

        exception = assertThrows(IllegalArgumentException.class, () -> {
            consumerCloudData.setCloudProviderShortName("1234567890123456");
        });
        assertEquals("cloudProviderShortName exceeds the max length", exception.getMessage());
    }

    @Test
    public void testSetCloudAccountId() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();
        String validId = "account123";
        consumerCloudData.setCloudAccountId(validId);
        assertEquals(validId, consumerCloudData.getCloudAccountId());

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            consumerCloudData.setCloudAccountId(new String(new char[256]).replace('\0', 'a'));
        });
        assertEquals("cloudAccountId exceeds the max length", exception.getMessage());
    }

    @Test
    public void testSetCloudInstanceId() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();
        String validId = "instance123";
        consumerCloudData.setCloudInstanceId(validId);
        assertEquals(validId, consumerCloudData.getCloudInstanceId());

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            consumerCloudData.setCloudInstanceId(
                new String(new char[171]).replace('\0', 'a'));
        });
        assertEquals("cloudInstanceId exceeds the max length", exception.getMessage());
    }

    @Test
    public void testAddCloudOfferingIdsSingleIdArrayInput() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();
        String validId = TestUtil.randomString("offering");
        consumerCloudData.addCloudOfferingIds(validId);

        assertEquals(List.of(validId), consumerCloudData.getCloudOfferingIds());
    }

    @Test
    public void testAddCloudOfferingIdsMultipleIdsArrayInput() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();
        String validId1 = TestUtil.randomString("offering");
        String validId2 = TestUtil.randomString("offering");
        consumerCloudData.addCloudOfferingIds(validId1, validId2);

        assertEquals(List.of(validId1, validId2), consumerCloudData.getCloudOfferingIds());
    }

    @Test
    public void testAddCloudOfferingIdsNullArrayInput() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            consumerCloudData.addCloudOfferingIds((String[]) null);
        });
        assertEquals("cloudOfferingIds is null", exception.getMessage());
    }

    @Test
    public void testAddCloudOfferingIdsArrayWithNullElement() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();
        String validId = TestUtil.randomString("offering");

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            consumerCloudData.addCloudOfferingIds(validId, null);
        });
        assertEquals("cloudOfferingIds contains null element", exception.getMessage());
    }

    @Test
    public void testAddCloudOfferingIdsExceedMaxLengthArrayInput() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();
        String validId = TestUtil.randomString("offering");
        consumerCloudData.addCloudOfferingIds(validId);

        // Generate a string that exceeds the maximum allowed length
        String longId = TestUtil.randomString(256, "a");

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            consumerCloudData.addCloudOfferingIds(longId);
        });
        assertEquals(
            "Combined cloudOfferingIds exceed the max length of 255 characters", exception.getMessage());
    }

    @Test
    public void testAddCloudOfferingIdsSingleIdCollectionInput() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();
        String validId = TestUtil.randomString("offering");
        consumerCloudData.addCloudOfferingIds(Collections.singletonList(validId));

        assertEquals(List.of(validId), consumerCloudData.getCloudOfferingIds());
    }

    @Test
    public void testAddCloudOfferingIdsMultipleIdsCollectionInput() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();
        String validId1 = TestUtil.randomString("offering");
        String validId2 = TestUtil.randomString("offering");
        consumerCloudData.addCloudOfferingIds(Arrays.asList(validId1, validId2));

        assertEquals(List.of(validId1, validId2), consumerCloudData.getCloudOfferingIds());
    }

    @Test
    public void testAddCloudOfferingIdsNullCollectionInput() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            consumerCloudData.addCloudOfferingIds((Collection<String>) null);
        });
        assertEquals("cloudOfferingIds is null or empty", exception.getMessage());
    }

    @Test
    public void testAddCloudOfferingIdsEmptyCollectionInput() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();
        String validId1 = TestUtil.randomString("offering");

        consumerCloudData.addCloudOfferingIds(validId1);
        consumerCloudData.addCloudOfferingIds(Collections.emptyList());

        assertThat(consumerCloudData.getCloudOfferingIds())
            .containsExactly(validId1);
    }

    @Test
    public void testAddCloudOfferingIdsCollectionWithNullElement() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();
        String validId = TestUtil.randomString("offering");

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            consumerCloudData.addCloudOfferingIds(Arrays.asList(validId, null));
        });
        assertEquals("cloudOfferingIds contains null element", exception.getMessage());
    }

    @Test
    public void testAddCloudOfferingIdsExceedMaxLengthCollectionInput() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();
        String validId = TestUtil.randomString("offering");
        consumerCloudData.addCloudOfferingIds(validId);

        // Generate a string that exceeds the maximum allowed length
        String longId = TestUtil.randomString(256, "a");

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            consumerCloudData.addCloudOfferingIds(Arrays.asList(longId));
        });
        assertEquals(
            "Combined cloudOfferingIds exceed the max length of 255 characters", exception.getMessage());
    }

    @Test
    public void testSetCloudOfferingIdsSingleIdArrayInput() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();
        String validId = TestUtil.randomString("offering");
        consumerCloudData.setCloudOfferingIds(validId);

        assertEquals(List.of(validId), consumerCloudData.getCloudOfferingIds());
    }

    @Test
    public void testSetCloudOfferingIdsMultipleIdsArrayInput() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();
        String validId1 = TestUtil.randomString("offering");
        String validId2 = TestUtil.randomString("offering");
        consumerCloudData.setCloudOfferingIds(validId1, validId2);

        assertEquals(List.of(validId1, validId2), consumerCloudData.getCloudOfferingIds());
    }

    @Test
    public void testSetCloudOfferingIdsNullArrayInput() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            consumerCloudData.setCloudOfferingIds((String[]) null);
        });
        assertEquals("cloudOfferingIds is null", exception.getMessage());
    }

    @Test
    public void testSetCloudOfferingIdsEmptyArrayInput() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();

        consumerCloudData.setCloudOfferingIds();

        assertThat(consumerCloudData.getCloudOfferingIds())
            .isEmpty();
    }

    @Test
    public void testSetCloudOfferingIdsArrayWithNullElement() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();
        String validId = TestUtil.randomString("offering");

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            consumerCloudData.setCloudOfferingIds(validId, null);
        });
        assertEquals("cloudOfferingIds contains null element", exception.getMessage());
    }

    @Test
    public void testSetCloudOfferingIdsExceedMaxLengthArrayInput() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();

        // Generate a string that exceeds the maximum allowed length
        String longId = TestUtil.randomString(256, "a");

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            consumerCloudData.setCloudOfferingIds(longId);
        });
        assertEquals(
            "Combined cloudOfferingIds exceed the max length of 255 characters", exception.getMessage());
    }

    @Test
    public void testSetCloudOfferingIdsSingleIdCollectionInput() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();
        String validId = TestUtil.randomString("offering");
        consumerCloudData.setCloudOfferingIds(Collections.singletonList(validId));

        assertEquals(List.of(validId), consumerCloudData.getCloudOfferingIds());
    }

    @Test
    public void testSetCloudOfferingIdsMultipleIdsCollectionInput() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();
        String validId1 = TestUtil.randomString("offering");
        String validId2 = TestUtil.randomString("offering");
        consumerCloudData.setCloudOfferingIds(Arrays.asList(validId1, validId2));

        assertEquals(List.of(validId1, validId2), consumerCloudData.getCloudOfferingIds());
    }

    @Test
    public void testSetCloudOfferingIdsNullCollectionInput() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            consumerCloudData.setCloudOfferingIds((Collection<String>) null);
        });
        assertEquals("cloudOfferingIds is null", exception.getMessage());
    }

    @Test
    public void testSetCloudOfferingIdsEmptyCollectionInput() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();

        consumerCloudData.setCloudOfferingIds(Collections.emptyList());

        assertThat(consumerCloudData.getCloudOfferingIds())
            .isEmpty();
    }

    @Test
    public void testSetCloudOfferingIdsCollectionWithNullElement() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();
        String validId = TestUtil.randomString("offering");

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            consumerCloudData.setCloudOfferingIds(Arrays.asList(validId, null));
        });
        assertEquals("cloudOfferingIds contains null element", exception.getMessage());
    }

    @Test
    public void testSetCloudOfferingIdsExceedMaxLengthCollectionInput() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();

        // Generate a string that exceeds the maximum allowed length
        String longId = TestUtil.randomString(256, "a");

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            consumerCloudData.setCloudOfferingIds(Collections.singletonList(longId));
        });
        assertEquals(
            "Combined cloudOfferingIds exceed the max length of 255 characters", exception.getMessage());
    }

    @Test
    public void testSetConsumer() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();
        Consumer consumer = new Consumer();
        consumerCloudData.setConsumer(consumer);
        assertEquals(consumer, consumerCloudData.getConsumer());
    }

    @Test
    public void testToString() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData()
            .setId("id123")
            .setCloudAccountId("account123")
            .setCloudProviderShortName("aws");

        String expected = "Consumer Cloud Data [id: id123," +
            " cloudAccountId: account123, cloudProviderShortName: aws]";
        assertEquals(expected, consumerCloudData.toString());
    }

    @Test
    public void testEqualsAndHashCode() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData()
            .setId("id123");

        ConsumerCloudData anotherConsumerCloudData = new ConsumerCloudData()
            .setId("id123");

        assertEquals(consumerCloudData, anotherConsumerCloudData);
        assertEquals(consumerCloudData.hashCode(), anotherConsumerCloudData.hashCode());

        anotherConsumerCloudData.setId("id456");
        assertNotEquals(consumerCloudData, anotherConsumerCloudData);
        assertNotEquals(consumerCloudData.hashCode(), anotherConsumerCloudData.hashCode());

        assertEquals(consumerCloudData, consumerCloudData);
        assertNotEquals(consumerCloudData, new Object());
    }
}
