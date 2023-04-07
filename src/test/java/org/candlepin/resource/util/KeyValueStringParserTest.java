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
package org.candlepin.resource.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.exceptions.BadRequestException;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;



/**
 * Test suite for the JobStateMapper class
 */
public class KeyValueStringParserTest {

    private static I18n i18n;

    @BeforeAll
    public static void init() {
        i18n = I18nFactory.getI18n(KeyValueStringParserTest.class, Locale.US, I18nFactory.FALLBACK);
    }

    private KeyValueStringParser buildParser() {
        return new KeyValueStringParser(i18n);
    }

    @Test
    public void testParseSinglePair() {
        String kvstring = "key:value";

        Optional<Pair<String, String>> output = this.buildParser().parseKeyValuePair(kvstring);
        assertNotNull(output);
        assertTrue(output.isPresent());

        Pair<String, String> pair = output.get();
        assertNotNull(pair);
        assertEquals("key", pair.getKey());
        assertEquals("value", pair.getValue());
    }

    @Test
    public void testParseSinglePairWithMultipleDelimiters() {
        String kvstring = "key:split:value";

        Optional<Pair<String, String>> output = this.buildParser().parseKeyValuePair(kvstring);
        assertNotNull(output);
        assertTrue(output.isPresent());

        Pair<String, String> pair = output.get();
        assertNotNull(pair);
        assertEquals("key", pair.getKey());
        assertEquals("split:value", pair.getValue());
    }

    @Test
    public void testParseSinglePairPermitsNullValues() {
        Optional<Pair<String, String>> output = this.buildParser().parseKeyValuePair(null);
        assertNotNull(output);
        assertFalse(output.isPresent());
    }

    @Test
    public void testParseSinglePairPermitsEmptyValues() {
        Optional<Pair<String, String>> output = this.buildParser().parseKeyValuePair("");
        assertNotNull(output);
        assertFalse(output.isPresent());
    }

    @Test
    public void testParseSinglePairRequiresDelimiter() {
        String kvstring = "some value";

        assertThrows(BadRequestException.class, () -> this.buildParser().parseKeyValuePair(kvstring));
    }

    @Test
    public void testParseIterablePairs() {
        Iterable<String> kvstrings = List.of(
            "key1:value1",
            "key2:value2",
            "key3:value3");

        Map<String, String> expected = new HashMap<>(Map.of(
            "key1", "value1",
            "key2", "value2",
            "key3", "value3"));

        List<Pair<String, String>> output = this.buildParser().parseKeyValuePairs(kvstrings);
        assertNotNull(output);
        assertEquals(expected.size(), output.size());

        for (Pair<String, String> pair : output) {
            assertNotNull(pair);

            assertTrue(expected.containsKey(pair.getKey()));
            assertEquals(expected.remove(pair.getKey()), pair.getValue());
        }
    }

    @Test
    public void testParseIterablePairsWithMultipleDelimiters() {
        Iterable<String> kvstrings = List.of(
            "key1:value1:extra",
            "key2:value2:split:data",
            "key3:value3:1:2:3");

        Map<String, String> expected = new HashMap<>(Map.of(
            "key1", "value1:extra",
            "key2", "value2:split:data",
            "key3", "value3:1:2:3"));

        List<Pair<String, String>> output = this.buildParser().parseKeyValuePairs(kvstrings);
        assertNotNull(output);
        assertEquals(expected.size(), output.size());

        for (Pair<String, String> pair : output) {
            assertNotNull(pair);

            assertTrue(expected.containsKey(pair.getKey()));
            assertEquals(expected.remove(pair.getKey()), pair.getValue());
        }

        assertTrue(expected.isEmpty());
    }

    @Test
    public void testParseIterablePairsPermitsNullCollection() {
        List<Pair<String, String>> output = this.buildParser().parseKeyValuePairs((Iterable) null);
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    public void testParseIterablePairsPermitsEmptyCollection() {
        List<Pair<String, String>> output = this.buildParser().parseKeyValuePairs(List.of());
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    public void testParseIterableDisallowsNullValuesInCollection() {
        Iterable<String> kvstrings = Arrays.asList("key1:value1", null, "key3:value3");

        assertThrows(BadRequestException.class, () -> this.buildParser().parseKeyValuePairs(kvstrings));
    }

    @Test
    public void testParseIterableDisallowsEmptyValuesInCollection() {
        Iterable<String> kvstrings = List.of("key1:value1", "", "key3:value3");

        assertThrows(BadRequestException.class, () -> this.buildParser().parseKeyValuePairs(kvstrings));
    }

    @Test
    public void testParseIterableDisallowsMalformedValuesInCollection() {
        Iterable<String> kvstrings = List.of("key1:value1", "notdelimited", "key3:value3");

        assertThrows(BadRequestException.class, () -> this.buildParser().parseKeyValuePairs(kvstrings));
    }

    @Test
    public void testParseIterablePairsOutputIsMutable() {
        Pair elem = Pair.of("test", "value");

        List<Pair<String, String>> output1 = this.buildParser().parseKeyValuePairs((Iterable) null);
        assertNotNull(output1);

        assertDoesNotThrow(() -> output1.add(elem));
        assertEquals(1, output1.size());
        assertTrue(output1.contains(elem));

        List<Pair<String, String>> output2 = this.buildParser().parseKeyValuePairs(List.of("key:value"));
        assertNotNull(output2);

        assertDoesNotThrow(() -> output2.add(elem));
        assertEquals(2, output2.size());
        assertTrue(output2.contains(elem));
    }

    @Test
    public void testParseArrayPairs() {
        String[] kvstrings = new String[] {
            "key1:value1",
            "key2:value2",
            "key3:value3" };

        Map<String, String> expected = new HashMap<>(Map.of(
            "key1", "value1",
            "key2", "value2",
            "key3", "value3"));

        List<Pair<String, String>> output = this.buildParser().parseKeyValuePairs(kvstrings);
        assertNotNull(output);
        assertEquals(expected.size(), output.size());

        for (Pair<String, String> pair : output) {
            assertNotNull(pair);

            assertTrue(expected.containsKey(pair.getKey()));
            assertEquals(expected.remove(pair.getKey()), pair.getValue());
        }

        assertTrue(expected.isEmpty());
    }

    @Test
    public void testParseArrayPairsWithMultipleDelimiters() {
        String[] kvstrings = new String[] {
            "key1:value1:extra",
            "key2:value2:split:data",
            "key3:value3:1:2:3" };

        Map<String, String> expected = new HashMap<>(Map.of(
            "key1", "value1:extra",
            "key2", "value2:split:data",
            "key3", "value3:1:2:3"));

        List<Pair<String, String>> output = this.buildParser().parseKeyValuePairs(kvstrings);
        assertNotNull(output);
        assertEquals(expected.size(), output.size());

        for (Pair<String, String> pair : output) {
            assertNotNull(pair);

            assertTrue(expected.containsKey(pair.getKey()));
            assertEquals(expected.remove(pair.getKey()), pair.getValue());
        }

        assertTrue(expected.isEmpty());
    }

    @Test
    public void testParseArrayPairsPermitsNullCollection() {
        List<Pair<String, String>> output = this.buildParser().parseKeyValuePairs((String[]) null);
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    public void testParseArrayPairsPermitsEmptyCollection() {
        List<Pair<String, String>> output = this.buildParser().parseKeyValuePairs();
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    public void testParseArrayDisallowsNullValuesInCollection() {
        String[] kvstrings = new String[] { "key1:value1", null, "key3:value3" };

        assertThrows(BadRequestException.class, () -> this.buildParser().parseKeyValuePairs(kvstrings));
    }

    @Test
    public void testParseArrayDisallowsEmptyValuesInCollection() {
        String[] kvstrings = new String[] { "key1:value1", "", "key3:value3" };

        assertThrows(BadRequestException.class, () -> this.buildParser().parseKeyValuePairs(kvstrings));
    }

    @Test
    public void testParseArrayDisallowsMalformedValuesInCollection() {
        String[] kvstrings = new String[] { "key1:value1", "notdelimited", "key3:value3" };

        assertThrows(BadRequestException.class, () -> this.buildParser().parseKeyValuePairs(kvstrings));
    }

    @Test
    public void testParseArrayPairsOutputIsMutable() {
        Pair elem = Pair.of("test", "value");

        List<Pair<String, String>> output1 = this.buildParser().parseKeyValuePairs();
        assertNotNull(output1);

        assertDoesNotThrow(() -> output1.add(elem));
        assertEquals(1, output1.size());
        assertTrue(output1.contains(elem));

        List<Pair<String, String>> output2 = this.buildParser().parseKeyValuePairs("key:value");
        assertNotNull(output2);

        assertDoesNotThrow(() -> output2.add(elem));
        assertEquals(2, output2.size());
        assertTrue(output2.contains(elem));
    }

    @Test
    public void testParseStreamPairs() {
        Stream<String> kvstrings = Stream.of(
            "key1:value1",
            "key2:value2",
            "key3:value3");

        Map<String, String> expected = new HashMap<>(Map.of(
            "key1", "value1",
            "key2", "value2",
            "key3", "value3"));

        Stream<Pair<String, String>> output = this.buildParser().parseKeyValuePairs(kvstrings);
        assertNotNull(output);

        output.forEach(pair -> {
            assertNotNull(pair);

            assertTrue(expected.containsKey(pair.getKey()));
            assertEquals(expected.remove(pair.getKey()), pair.getValue());
        });

        assertTrue(expected.isEmpty());
    }

    @Test
    public void testParseStreamPairsWithMultipleDelimiters() {
        Stream<String> kvstrings = Stream.of(
            "key1:value1:extra",
            "key2:value2:split:data",
            "key3:value3:1:2:3");

        Map<String, String> expected = new HashMap<>(Map.of(
            "key1", "value1:extra",
            "key2", "value2:split:data",
            "key3", "value3:1:2:3"));

        Stream<Pair<String, String>> output = this.buildParser().parseKeyValuePairs(kvstrings);
        assertNotNull(output);

        output.forEach(pair -> {
            assertNotNull(pair);

            assertTrue(expected.containsKey(pair.getKey()));
            assertEquals(expected.remove(pair.getKey()), pair.getValue());
        });

        assertTrue(expected.isEmpty());
    }

    @Test
    public void testParseStreamPairsPermitsNullCollection() {
        Stream<Pair<String, String>> output = this.buildParser().parseKeyValuePairs((Stream) null);
        assertNotNull(output);
        assertEquals(0, output.count());
    }

    @Test
    public void testParseStreamPairsPermitsEmptyCollection() {
        Stream<Pair<String, String>> output = this.buildParser().parseKeyValuePairs(Stream.empty());
        assertNotNull(output);
        assertEquals(0, output.count());
    }

    @Test
    public void testParseStreamDisallowsNullValuesInCollection() {
        Stream<String> kvstrings = Stream.of("key1:value1", null, "key3:value3");

        // Impl note: due to how the stream mapper works, this won't trigger an exception until
        // we process the invalid value. We'll trigger one below with a terminal operation.
        Stream<Pair<String, String>> output = this.buildParser().parseKeyValuePairs(kvstrings);

        assertThrows(BadRequestException.class, () -> output.collect(Collectors.toList()));
    }

    @Test
    public void testParseStreamDisallowsEmptyValuesInCollection() {
        Stream<String> kvstrings = Stream.of("key1:value1", "", "key3:value3");

        // Impl note: due to how the stream mapper works, this won't trigger an exception until
        // we process the invalid value. We'll trigger one below with a terminal operation.
        Stream<Pair<String, String>> output = this.buildParser().parseKeyValuePairs(kvstrings);

        assertThrows(BadRequestException.class, () -> output.collect(Collectors.toList()));
    }

    @Test
    public void testParseStreamDisallowsMalformedValuesInCollection() {
        Stream<String> kvstrings = Stream.of("key1:value1", "notdelimited", "key3:value3");

        // Impl note: due to how the stream mapper works, this won't trigger an exception until
        // we process the invalid value. We'll trigger one below with a terminal operation.
        Stream<Pair<String, String>> output = this.buildParser().parseKeyValuePairs(kvstrings);

        assertThrows(BadRequestException.class, () -> output.collect(Collectors.toList()));
    }

    @Test
    public void testMapIterablePairs() {
        Iterable<String> kvstrings = List.of(
            "key1:value1",
            "key2:value2",
            "key3:value3");

        Map<String, String> expected = Map.of(
            "key1", "value1",
            "key2", "value2",
            "key3", "value3");

        Map<String, String> output = this.buildParser().mapKeyValuePairs(kvstrings);
        assertNotNull(output);
        assertEquals(expected, output);
    }

    @Test
    public void testMapIterablePairsWithMultipleDelimiters() {
        Iterable<String> kvstrings = List.of(
            "key1:value1:extra",
            "key2:value2:split:data",
            "key3:value3:1:2:3");

        Map<String, String> expected = Map.of(
            "key1", "value1:extra",
            "key2", "value2:split:data",
            "key3", "value3:1:2:3");

        Map<String, String> output = this.buildParser().mapKeyValuePairs(kvstrings);
        assertNotNull(output);
        assertEquals(expected, output);
    }

    @Test
    public void testMapIterablePairsDeduplicatesKeys() {
        Iterable<String> kvstrings = List.of(
            "key1:value1",
            "key2:value2",
            "key1:value3");

        Map<String, String> expected = Map.of(
            "key1", "value3",
            "key2", "value2");

        Map<String, String> output = this.buildParser().mapKeyValuePairs(kvstrings);
        assertNotNull(output);
        assertEquals(expected, output);
    }

    @Test
    public void testMapIterablePairsPermitsNullCollection() {
        Map<String, String> output = this.buildParser().mapKeyValuePairs((Iterable) null);
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    public void testMapIterablePairsPermitsEmptyCollection() {
        Map<String, String> output = this.buildParser().mapKeyValuePairs(List.of());
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    public void testMapIterableDisallowsNullValuesInCollection() {
        Iterable<String> kvstrings = Arrays.asList("key1:value1", null, "key3:value3");

        assertThrows(BadRequestException.class, () -> this.buildParser().mapKeyValuePairs(kvstrings));
    }

    @Test
    public void testMapIterableDisallowsEmptyValuesInCollection() {
        Iterable<String> kvstrings = List.of("key1:value1", "", "key3:value3");

        assertThrows(BadRequestException.class, () -> this.buildParser().mapKeyValuePairs(kvstrings));
    }

    @Test
    public void testMapIterableDisallowsMalformedValuesInCollection() {
        Iterable<String> kvstrings = List.of("key1:value1", "notdelimited", "key3:value3");

        assertThrows(BadRequestException.class, () -> this.buildParser().mapKeyValuePairs(kvstrings));
    }

    @Test
    public void testMapIterablePairsOutputIsMutable() {
        Map<String, String> output1 = this.buildParser().mapKeyValuePairs((Iterable) null);
        assertNotNull(output1);

        assertDoesNotThrow(() -> output1.put("test", "value"));
        assertEquals(1, output1.size());
        assertTrue(output1.containsKey("test"));
        assertEquals("value", output1.get("test"));

        Map<String, String> output2 = this.buildParser().mapKeyValuePairs(List.of("key:value"));
        assertNotNull(output2);

        assertDoesNotThrow(() -> output2.put("test", "value"));
        assertEquals(2, output2.size());
        assertTrue(output2.containsKey("test"));
        assertEquals("value", output2.get("test"));
    }

    @Test
    public void testMapArrayPairs() {
        String[] kvstrings = new String[] {
            "key1:value1",
            "key2:value2",
            "key3:value3" };

        Map<String, String> expected = Map.of(
            "key1", "value1",
            "key2", "value2",
            "key3", "value3");

        Map<String, String> output = this.buildParser().mapKeyValuePairs(kvstrings);
        assertNotNull(output);
        assertEquals(expected, output);
    }

    @Test
    public void testMapArrayPairsWithMultipleDelimiters() {
        String[] kvstrings = new String[] {
            "key1:value1:extra",
            "key2:value2:split:data",
            "key3:value3:1:2:3" };

        Map<String, String> expected = Map.of(
            "key1", "value1:extra",
            "key2", "value2:split:data",
            "key3", "value3:1:2:3");

        Map<String, String> output = this.buildParser().mapKeyValuePairs(kvstrings);
        assertNotNull(output);
        assertEquals(expected, output);
    }

    @Test
    public void testMapArrayPairsDeduplicatesKeys() {
        String[] kvstrings = new String[] {
            "key1:value1",
            "key2:value2",
            "key1:value3" };

        Map<String, String> expected = Map.of(
            "key1", "value3",
            "key2", "value2");

        Map<String, String> output = this.buildParser().mapKeyValuePairs(kvstrings);
        assertNotNull(output);
        assertEquals(expected, output);
    }

    @Test
    public void testMapArrayPairsPermitsNullCollection() {
        Map<String, String> output = this.buildParser().mapKeyValuePairs((String[]) null);
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    public void testMapArrayPairsPermitsEmptyCollection() {
        Map<String, String> output = this.buildParser().mapKeyValuePairs();
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    public void testMapArrayDisallowsNullValuesInCollection() {
        String[] kvstrings = new String[] { "key1:value1", null, "key3:value3" };

        assertThrows(BadRequestException.class, () -> this.buildParser().mapKeyValuePairs(kvstrings));
    }

    @Test
    public void testMapArrayDisallowsEmptyValuesInCollection() {
        String[] kvstrings = new String[] { "key1:value1", "", "key3:value3" };

        assertThrows(BadRequestException.class, () -> this.buildParser().mapKeyValuePairs(kvstrings));
    }

    @Test
    public void testMapArrayDisallowsMalformedValuesInCollection() {
        String[] kvstrings = new String[] { "key1:value1", "notdelimited", "key3:value3" };

        assertThrows(BadRequestException.class, () -> this.buildParser().mapKeyValuePairs(kvstrings));
    }

    @Test
    public void testMapArrayPairsOutputIsMutable() {
        Map<String, String> output1 = this.buildParser().mapKeyValuePairs();
        assertNotNull(output1);

        assertDoesNotThrow(() -> output1.put("test", "value"));
        assertEquals(1, output1.size());
        assertTrue(output1.containsKey("test"));
        assertEquals("value", output1.get("test"));

        Map<String, String> output2 = this.buildParser().mapKeyValuePairs("key:value");
        assertNotNull(output2);

        assertDoesNotThrow(() -> output2.put("test", "value"));
        assertEquals(2, output2.size());
        assertTrue(output2.containsKey("test"));
        assertEquals("value", output2.get("test"));
    }

}
