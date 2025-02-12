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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;



public class ConsumerCloudDataTest {

    @Test
    public void testSetCloudProviderShortName() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();
        String validName = "aws";
        consumerCloudData.setCloudProviderShortName(validName);
        assertEquals(validName, consumerCloudData.getCloudProviderShortName());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "   ", "\t" })
    public void testSetCloudProviderShortNameWithNullAndEmptyInputs(String value) {
        ConsumerCloudData ccdata = new ConsumerCloudData();

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            ccdata.setCloudProviderShortName(value);
        });

        assertEquals("cloudProviderShortName is null or empty", exception.getMessage());
    }

    @Test
    public void testSetCloudProviderShortNameWithLongValue() {
        ConsumerCloudData ccdata = new ConsumerCloudData();

        String value = "a".repeat(ConsumerCloudData.CLOUD_PROVIDER_MAX_LENGTH + 1);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            ccdata.setCloudProviderShortName(value);
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

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "   ", "\t" })
    public void testAddCloudOfferingIdsArrayWithNullAndBlankElements(String value) {
        ConsumerCloudData ccdata = new ConsumerCloudData();

        ConsumerCloudData output1 = ccdata.addCloudOfferingIds(value, value, value);
        assertSame(ccdata, output1);

        ConsumerCloudData output2 = ccdata.addCloudOfferingIds(value, value, value);
        assertSame(ccdata, output2);

        assertEquals(List.of(), ccdata.getCloudOfferingIds());
    }

    @Test
    public void testAddCloudOfferingIdsArrayWithNullAndBlankMixedElements() {
        ConsumerCloudData ccdata = new ConsumerCloudData();

        ConsumerCloudData output1 = ccdata.addCloudOfferingIds("", null, " ", "   ", "\t");
        assertSame(ccdata, output1);

        ConsumerCloudData output2 = ccdata.addCloudOfferingIds("", null, " ", "   ", "\t");
        assertSame(ccdata, output2);

        assertEquals(List.of(), ccdata.getCloudOfferingIds());
    }

    @Test
    public void testAddCloudOfferingIdsArrayWithNullAndBlankAndValidMixedElements() {
        ConsumerCloudData ccdata = new ConsumerCloudData();
        String offeringId1 = "test_offering-1";
        String offeringId2 = "test_offering-2";

        ConsumerCloudData output1 = ccdata.addCloudOfferingIds("", null, offeringId1, " ", "   ", "\t");
        assertSame(ccdata, output1);

        ConsumerCloudData output2 = ccdata.addCloudOfferingIds("", null, offeringId2, " ", "   ", "\t");
        assertSame(ccdata, output2);

        assertEquals(List.of(offeringId1, offeringId2), ccdata.getCloudOfferingIds());
    }

    @Test
    public void testAddCloudOfferingIdsArrayWithNullElement() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();
        String validId = TestUtil.randomString("offering");

        consumerCloudData.addCloudOfferingIds(validId, null);

        assertEquals(List.of(validId), consumerCloudData.getCloudOfferingIds());
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
        consumerCloudData.addCloudOfferingIds(List.of(validId));

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

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "   ", "\t" })
    public void testAddCloudOfferingIdsCollectionWithNullAndBlankElements(String value) {
        ConsumerCloudData ccdata = new ConsumerCloudData();

        ConsumerCloudData output1 = ccdata.addCloudOfferingIds(Arrays.asList(value, value, value));
        assertSame(ccdata, output1);

        ConsumerCloudData output2 = ccdata.addCloudOfferingIds(Arrays.asList(value, value, value));
        assertSame(ccdata, output2);

        assertEquals(List.of(), ccdata.getCloudOfferingIds());
    }

    @Test
    public void testAddCloudOfferingIdsCollectionWithNullAndBlankMixedElements() {
        ConsumerCloudData ccdata = new ConsumerCloudData();

        ConsumerCloudData output1 = ccdata.addCloudOfferingIds(Arrays.asList("", null, " ", "   ", "\t"));
        assertSame(ccdata, output1);

        ConsumerCloudData output2 = ccdata.addCloudOfferingIds(Arrays.asList("", null, " ", "   ", "\t"));
        assertSame(ccdata, output2);

        assertEquals(List.of(), ccdata.getCloudOfferingIds());
    }

    @Test
    public void testAddCloudOfferingIdsCollectionWithNullAndBlankAndValidMixedElements() {
        ConsumerCloudData ccdata = new ConsumerCloudData();
        String offeringId1 = "test_offering-1";
        String offeringId2 = "test_offering-2";

        ConsumerCloudData output1 = ccdata.addCloudOfferingIds(
            Arrays.asList("", null, offeringId1, " ", "   ", "\t"));
        assertSame(ccdata, output1);

        ConsumerCloudData output2 = ccdata.addCloudOfferingIds(
            Arrays.asList("", null, offeringId2, " ", "   ", "\t"));
        assertSame(ccdata, output2);

        assertEquals(List.of(offeringId1, offeringId2), ccdata.getCloudOfferingIds());
    }

    @Test
    public void testAddCloudOfferingIdsEmptyCollectionInput() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();
        String validId1 = TestUtil.randomString("offering");

        consumerCloudData.addCloudOfferingIds(validId1);
        consumerCloudData.addCloudOfferingIds(List.of());

        assertThat(consumerCloudData.getCloudOfferingIds())
            .containsExactly(validId1);
    }

    @Test
    public void testAddCloudOfferingIdsCollectionWithNullElement() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();
        String validId = TestUtil.randomString("offering");

        consumerCloudData.addCloudOfferingIds(Arrays.asList(validId, null));

        assertEquals(List.of(validId), consumerCloudData.getCloudOfferingIds());
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

        consumerCloudData.setCloudOfferingIds((String[]) null);

        assertThat(consumerCloudData.getCloudOfferingIds())
            .isEmpty();
    }

    @Test
    public void testSetCloudOfferingIdsEmptyArrayInput() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();

        consumerCloudData.setCloudOfferingIds(new String[] {});

        assertThat(consumerCloudData.getCloudOfferingIds())
            .isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "   ", "\t" })
    public void testSetCloudOfferingIdsArrayWithNullAndBlankElements(String value) {
        ConsumerCloudData ccdata = new ConsumerCloudData();

        ConsumerCloudData output = ccdata.setCloudOfferingIds(value, value, value);
        assertSame(ccdata, output);

        assertEquals(List.of(), ccdata.getCloudOfferingIds());
    }

    @Test
    public void testSetCloudOfferingIdsArrayWithNullAndBlankMixedElements() {
        ConsumerCloudData ccdata = new ConsumerCloudData();

        ConsumerCloudData output = ccdata.setCloudOfferingIds("", null, " ", "   ", "\t");
        assertSame(ccdata, output);

        assertEquals(List.of(), ccdata.getCloudOfferingIds());
    }

    @Test
    public void testSetCloudOfferingIdsArrayWithNullAndBlankAndValidMixedElements() {
        ConsumerCloudData ccdata = new ConsumerCloudData();
        String offeringId = "test_offering";

        ConsumerCloudData output = ccdata.setCloudOfferingIds("", null, offeringId, " ", "   ", "\t");
        assertSame(ccdata, output);

        assertEquals(List.of(offeringId), ccdata.getCloudOfferingIds());
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
        consumerCloudData.setCloudOfferingIds(List.of(validId));

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

        consumerCloudData.setCloudOfferingIds((Collection<String>) null);

        assertThat(consumerCloudData.getCloudOfferingIds())
            .isEmpty();
    }

    @Test
    public void testSetCloudOfferingIdsEmptyCollectionInput() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();

        consumerCloudData.setCloudOfferingIds(List.of());

        assertThat(consumerCloudData.getCloudOfferingIds())
            .isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "   ", "\t" })
    public void testSetCloudOfferingIdsCollectionWithNullAndBlankElements(String value) {
        ConsumerCloudData ccdata = new ConsumerCloudData();

        ConsumerCloudData output = ccdata.setCloudOfferingIds(Arrays.asList(value, value, value));
        assertSame(ccdata, output);

        assertEquals(List.of(), ccdata.getCloudOfferingIds());
    }

    @Test
    public void testSetCloudOfferingIdsCollectionWithNullAndBlankMixedElements() {
        ConsumerCloudData ccdata = new ConsumerCloudData();

        ConsumerCloudData output = ccdata.setCloudOfferingIds(Arrays.asList("", null, " ", "   ", "\t"));
        assertSame(ccdata, output);

        assertEquals(List.of(), ccdata.getCloudOfferingIds());
    }

    @Test
    public void testSetCloudOfferingIdsCollectionWithNullAndBlankAndValidMixedElements() {
        ConsumerCloudData ccdata = new ConsumerCloudData();
        String offeringId = "test_offering";

        ConsumerCloudData output = ccdata.setCloudOfferingIds(
            Arrays.asList("", null, offeringId, " ", "   ", "\t"));
        assertSame(ccdata, output);

        assertEquals(List.of(offeringId), ccdata.getCloudOfferingIds());
    }

    @Test
    public void testSetCloudOfferingIdsExceedMaxLengthCollectionInput() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();

        // Generate a string that exceeds the maximum allowed length
        String longId = TestUtil.randomString(256, "a");

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            consumerCloudData.setCloudOfferingIds(List.of(longId));
        });
        assertEquals("Combined cloudOfferingIds exceed the max length of 255 characters",
            exception.getMessage());
    }

    @Test
    public void testSetConsumer() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData();
        Consumer consumer = new Consumer();
        consumerCloudData.setConsumer(consumer);
        assertEquals(consumer, consumerCloudData.getConsumer());
    }

    @Test
    void testUpdateFromNull() {
        ConsumerCloudData original = new ConsumerCloudData()
            .setCloudProviderShortName("aws")
            .setCloudAccountId("acc1")
            .setCloudInstanceId("inst1")
            .setCloudOfferingIds(List.of("off1", "off2"));

        original.updateFrom(null);

        assertEquals("aws", original.getCloudProviderShortName());
        assertEquals("acc1", original.getCloudAccountId());
        assertEquals("inst1", original.getCloudInstanceId());
        assertEquals(List.of("off1", "off2"), original.getCloudOfferingIds());
    }

    @Test
    void testUpdateFromUpdatesOnlyChangedFields() {
        ConsumerCloudData original = new ConsumerCloudData()
            .setCloudProviderShortName("aws")
            .setCloudAccountId("acc1")
            .setCloudInstanceId("inst1")
            .setCloudOfferingIds(List.of("off1", "off2"));
        ConsumerCloudData update = new ConsumerCloudData()
            .setCloudProviderShortName("gcp")
            .setCloudAccountId("acc2")
            .setCloudInstanceId("inst2")
            .setCloudOfferingIds(List.of("off3", "off4"));

        original.updateFrom(update);

        assertEquals("gcp", original.getCloudProviderShortName());
        assertEquals("acc2", original.getCloudAccountId());
        assertEquals("inst2", original.getCloudInstanceId());
        assertEquals(List.of("off3", "off4"), original.getCloudOfferingIds());
    }

    @Test
    void testUpdateFromDoesNotUpdateWhenNewValuesAreNull() {
        ConsumerCloudData original = new ConsumerCloudData()
            .setCloudProviderShortName("aws")
            .setCloudAccountId("acc1")
            .setCloudInstanceId("inst1")
            .setCloudOfferingIds(List.of("off1", "off2"));
        ConsumerCloudData update = new ConsumerCloudData();

        original.updateFrom(update);

        assertEquals("aws", original.getCloudProviderShortName());
        assertEquals("acc1", original.getCloudAccountId());
        assertEquals("inst1", original.getCloudInstanceId());
        assertEquals(List.of("off1", "off2"), original.getCloudOfferingIds());
    }

    @Test
    public void testToString() {
        ConsumerCloudData consumerCloudData = new ConsumerCloudData()
            .setCloudAccountId("account123")
            .setCloudProviderShortName("aws")
            .setCloudOfferingIds(List.of("offering1", "offering2"))
            .setConsumer(new Consumer().setUuid("consumer123"));

        String expected = String.format("ConsumerCloudData [consumerUuid: %s, cloudProviderShortName: %s," +
            " cloudAccountId: %s, cloudInstanceId: %s, cloudOfferingIds: %s]",
            consumerCloudData.getConsumer().getUuid(), consumerCloudData.getCloudProviderShortName(),
            consumerCloudData.getCloudAccountId(), consumerCloudData.getCloudInstanceId(),
            consumerCloudData.getCloudOfferingIds());

        assertEquals(expected, consumerCloudData.toString());
    }

}
