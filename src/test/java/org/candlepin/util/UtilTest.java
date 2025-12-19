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
package org.candlepin.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.candlepin.dto.api.server.v1.AttributeDTO;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Appender;

import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;



/**
 * Test Class for the Util class
 */
public class UtilTest {

    private Logger utilLogger;
    private Appender mockapp;

    @BeforeEach
    public void init() {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        utilLogger = lc.getLogger(Util.class);
        mockapp = mock(Appender.class);
        utilLogger.addAppender(mockapp);
        utilLogger.setLevel(Level.DEBUG);
    }

    @Test
    public void testRandomUUIDS() {
        assertNotSame(Util.generateUUID(), Util.generateUUID());
    }

    @Test
    public void tomorrow() {
        // Due to the whole doing-stuff-takes-time thing, we need to allow a handful of milliseconds
        // to pass over this test. We'll assume things are functioning correctly if the actual result
        // is within a tolerable amount of milliseconds from our calculated time.
        long tolerance = 500;

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 1);

        long expected = cal.getTimeInMillis();
        long actual = Util.yesterday().getTime();

        assertTrue(actual - expected <= tolerance);
    }

    @Test
    public void yesterday() {
        // Due to the whole doing-stuff-takes-time thing, we need to allow a handful of milliseconds
        // to pass over this test. We'll assume things are functioning correctly if the actual result
        // is within a tolerable amount of milliseconds from our calculated time.
        long tolerance = 500;

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -1);

        long expected = cal.getTimeInMillis();
        long actual = Util.yesterday().getTime();

        assertTrue(actual - expected <= tolerance);
    }

    @Test
    public void midnight() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MILLISECOND, 0);

        Date expected = cal.getTime();
        Date actual = Util.midnight();

        assertEquals(expected, actual);
    }

    // This is pretty silly - basically doing the same thing the
    // method under test does...
    @Test
    public void addDaysToDt() {
        Calendar c = Calendar.getInstance();
        c.setTime(new Date());
        c.add(Calendar.DAY_OF_MONTH, 10);
        int future = c.get(Calendar.DAY_OF_MONTH);
        c.setTime(Util.addDaysToDt(10));
        assertEquals(future, c.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void addMinutesToDt() {
        Calendar c = Calendar.getInstance();
        c.setTime(new Date());
        c.add(Calendar.MINUTE, 10);
        int future = c.get(Calendar.MINUTE);
        c.setTime(Util.addMinutesToDt(10));
        assertEquals(future, c.get(Calendar.MINUTE));
    }

    @Test
    public void negativeAddDaysToDt() {
        Calendar c = Calendar.getInstance();
        c.setTime(new Date());
        c.add(Calendar.DAY_OF_MONTH, -10);
        int past = c.get(Calendar.DAY_OF_MONTH);

        c.setTime(Util.addDaysToDt(-10));
        assertEquals(past, c.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void equals() {
        assertTrue(Util.equals(null, null));
        assertTrue(Util.equals("foo", "foo"));
        assertFalse(Util.equals("foo", "bar"));
        assertFalse(Util.equals(null, "foo"));
    }

    @Test
    public void uniquelong() {
        long[] unique = new long[10000];
        for (int i = 0; i < unique.length; i++) {
            unique[i] = Util.generateUniqueLong();
        }

        Set<Long> nodupes = new HashSet<>();
        for (int i = 0; i < unique.length; i++) {
            nodupes.add(unique[i]);
        }

        // if they are truly unique, the original array should
        // not have had any duplicates. Therefore, the Set
        // will have all of the same elements that the original
        // array had.
        assertEquals(unique.length, nodupes.size());
    }

    @Test
    public void base64() {
        String foo = "this will be cool";
        String basefoo = Util.toBase64(foo.getBytes());
        assertNotNull(basefoo);
        String decoded = new String(Base64.decodeBase64(basefoo.getBytes()));
        assertEquals(decoded, foo);
    }

    @Test
    public void utcformat() {
        SimpleDateFormat sdf = Util.getUTCDateFormat();
        assertNotNull(sdf);
        assertEquals("UTC", sdf.getTimeZone().getID());
    }

    @Test
    public void readfile() throws IOException {
        File tmpfile = File.createTempFile("utiltest", "tmp");
        Writer out = new FileWriter(tmpfile);
        out.write("you're right");
        out.close();

        // read file appends a newline
        String line = Util.readFile(new FileInputStream(tmpfile));
        assertEquals("you're right\n", line);
        tmpfile.delete();
    }

    @Test
    public void json() {
        String test = "I Love JSON";
        String json = Util.toJson(test);
        String result = Util.fromJson(json, String.class);
        assertEquals(result, test);
    }

    @Test
    public void className() {
        assertEquals("UtilTest", Util.getClassName(this.getClass()));
    }

    @Test
    public void testIsUuid() {
        assertTrue(Util.isUuid("78d7e200-b7d6-4cfe-b7a9-5700e8094df3"));
        // Mixed case should work as well
        assertTrue(Util.isUuid("78d7E200-b7d6-4cfe-b7a9-5700e8094DF3"));
        // Different length should not
        assertFalse(Util.isUuid("78d7E200-b7d6-4cfe-b7a9-5700e8094DF34"));
        assertFalse(Util.isUuid("78d7e200-b7d6-4cf-b7a9-5700e8094df3"));
        // No Gs
        assertFalse(Util.isUuid("78d7E200-b7d6-4cfe-b7g9-5700e8094DF3"));
    }

    @Test
    public void testReverseEndian() {
        assertEquals("3412", Util.reverseEndian("1234"));
        // Supporting adding zeroes for padding probably isn't necessary,
        // but just in case this method is used elsewhere
        assertEquals("2301", Util.reverseEndian("123"));
        // Test small inputs
        assertEquals("12", Util.reverseEndian("12"));
        // This case might be useful to keep in mind
        assertEquals("01", Util.reverseEndian("1"));
        // Test smaller inputs
        assertEquals("", Util.reverseEndian(""));
    }

    /*
     * By the time this method has been called, we should have already verified
     * uuidness.  We shouldn't have to worry about length or bad characters
     */
    @Test
    public void testTransformUuid() {
        String uuid = "78d7e200-b7d6-4cfe-b7a9-5700e8094df3";
        String expected = "00e2d778-d6b7-fe4c-b7a9-5700e8094df3";
        assertEquals(expected, Util.transformUuid(uuid));
    }

    @Test
    public void testPossibleUuids() {
        String uuid = "78d7e200-b7d6-4cfe-b7a9-5700e8094df3";
        String expectedReversed = "00e2d778-d6b7-fe4c-b7a9-5700e8094df3";
        List<String> result = Util.getPossibleUuids(uuid);
        assertEquals(2, result.size());
        assertTrue(result.contains(uuid));
        assertTrue(result.contains(expectedReversed));
    }

    @Test
    public void testPossibleUuidsForcesLowerCase() {
        String uuid = "78d7e200-b7d6-4cfE-b7a9-5700e8094DF3";
        String expectedUuid = "78d7e200-b7d6-4cfe-b7a9-5700e8094df3";
        String expectedReversed = "00e2d778-d6b7-fe4c-b7a9-5700e8094df3";
        List<String> result = Util.getPossibleUuids(uuid);
        assertEquals(2, result.size());
        // Both the uuid and endian-reversed id should be lower case
        assertTrue(result.contains(expectedUuid));
        assertTrue(result.contains(expectedReversed));
    }

    @Test
    public void testPossibleUuidsWithNonUuid() {
        String id = "some_non_uuid";
        List<String> result = Util.getPossibleUuids(id);
        assertEquals(1, result.size());
        assertTrue(result.contains(id));
    }

    @Test
    public void testPossibleUuidsWithEmptyString() {
        String id = "";
        List<String> result = Util.getPossibleUuids(id);
        assertEquals(1, result.size());
        assertTrue(result.contains(id));
    }

    @Test
    public void testPossibleUuidsWithNull() {
        String id = null;
        List<String> result = Util.getPossibleUuids(id);
        assertEquals(1, result.size());
        assertTrue(result.contains(id));
    }

    private interface TestClosable {
        void close();
    }

    @Test
    public void testGetHostname() throws Exception {
        String hostname = Util.getHostname();
        assertNotNull(hostname);
    }

    @Test
    public void testParseOffsetDateTimeFromZonedDateTimePattern() {
        DateTimeFormatter formatter = new DateTimeFormatterBuilder()
            .appendPattern("EEE, dd MMM yyyy HH:mm:ss z")
            .toFormatter();
        OffsetDateTime actualDate = Util.parseOffsetDateTime(formatter, "Mon, 11 Jan 2021 15:30:05 EST");
        Assertions.assertEquals("2021-01-11T15:30:05-05:00", actualDate.toString());
    }

    @Test
    public void testParseOffsetDateTimeFromLocalDateTimePattern() {
        DateTimeFormatter formatter = new DateTimeFormatterBuilder()
            .appendPattern("EEE, dd MMM yyyy HH:mm:ss")
            .toFormatter();
        OffsetDateTime actualDate = Util.parseOffsetDateTime(formatter, "Mon, 11 Jan 2021 15:30:05");
        Assertions.assertEquals("2021-01-11T15:30:05Z", actualDate.toString());
    }

    @Test
    public void testParseOffsetDateTimeFromLocalDatePattern() {
        DateTimeFormatter formatter = new DateTimeFormatterBuilder()
            .appendPattern("EEE, dd MMM yyyy")
            .toFormatter();
        OffsetDateTime actualDate = Util.parseOffsetDateTime(formatter, "Mon, 11 Jan 2021");
        Assertions.assertEquals("2021-01-11T00:00Z", actualDate.toString());
    }

    @Nested
    class ToListTest {

        @Test
        @DisplayName("A null cannot be split")
        void nullString() {
            assertTrue(Util.toList(null).isEmpty());
        }

        @ParameterizedTest(name = "An invalid value: \"{0}\" cannot be split")
        @ValueSource(strings = {"", " ", " , "})
        void emptyString(String list) {
            assertTrue(Util.toList(list).isEmpty());
        }

        @ParameterizedTest(name = "A valid value: \"{0}\" can be split")
        @ValueSource(strings = {"item1, item2", " item1,item2 ", " item1 , item2 "})
        void validString(String list) {
            List<String> items = Util.toList(list);

            assertEquals(2, items.size());
            assertTrue(items.contains("item1"));
            assertTrue(items.contains("item2"));
        }
    }

    @Nested
    class EncodeUrlTest {

        @Test
        void nullString() {
            assertEquals("", Util.encodeUrl(null));
        }

        @Test
        void encodesSpecialCharacters() {
            String text = "/org!  #$%&'()*+,/123:;=?@[]\"-.<>\\^_`{|}~£円/$env/";
            String expected = "%2Forg%21++%23%24%25%26%27%28%29*%2B%2C%2F123%3A%3B%3D%3F%40%5B%5D%22-." +
                "%3C%3E%5C%5E_%60%7B%7C%7D%7E%C2%A3%E5%86%86%2F%24env%2F";
            assertEquals(expected, Util.encodeUrl(text));
        }
    }

    public static AttributeDTO buildAttributeDTO(String name, String value) {
        return new AttributeDTO()
            .name(name)
            .value(value);
    }

    @Nested
    class AttributesDTOToMapTests {
        @Test
        public void testToMap() {
            List<AttributeDTO> attribList = List.of(
                buildAttributeDTO("test_attrib-1", "test_value"),
                buildAttributeDTO("test_attrib-2", "test_value-2"),
                buildAttributeDTO("test_attrib-3", ""));

            Map<String, String> attribMap = Util.toMap(attribList);

            assertNotNull(attribMap);
            assertEquals(attribList.size(), attribMap.size());

            for (AttributeDTO attrib : attribList) {
                assertTrue(attribMap.containsKey(attrib.getName()));
                assertEquals(attrib.getValue(), attribMap.get(attrib.getName()));
            }
        }

        @Test
        public void testToMapHandlesNullCollections() {
            Map<String, String> attribMap = Util.toMap(null);

            assertNull(attribMap);
        }

        @Test
        public void testToMapHandlesEmptyCollections() {
            Map<String, String> attribMap = Util.toMap(Collections.emptyList());

            assertNotNull(attribMap);
            assertTrue(attribMap.isEmpty());
        }

        @Test
        public void testToMapHandlesDuplicateKeys() {
            String key = "test_attrib";
            String valueA = "test_value-A";
            String valueB = "test_value-B";

            List<AttributeDTO> attribList = List.of(
                buildAttributeDTO(key, valueA),
                buildAttributeDTO("test_attrib-2", "some attribute"),
                buildAttributeDTO(key, valueB));

            Map<String, String> attribMap = Util.toMap(attribList);

            assertNotNull(attribMap);
            assertEquals(2, attribMap.size());

            assertTrue(attribMap.containsKey(key));
            assertEquals(valueB, attribMap.get(key));
        }

        @Test
        public void testToMapDiscardsNullAttributes() {
            List<AttributeDTO> attribList = new ArrayList<>();
            attribList.add(null);
            attribList.add(buildAttributeDTO("test_attrib-1", "test_value"));
            attribList.add(null);
            attribList.add(buildAttributeDTO("test_attrib-2", "another_value"));
            attribList.add(null);

            Map<String, String> attribMap = Util.toMap(attribList);

            assertNotNull(attribMap);
            assertEquals(2, attribMap.size());

            for (AttributeDTO attrib : attribList) {
                if (attrib == null) {
                    continue;
                }

                assertTrue(attribMap.containsKey(attrib.getName()));
                assertEquals(attrib.getValue(), attribMap.get(attrib.getName()));
            }
        }

        @Test
        public void testToMapDiscardsAttributesWithEmptyKeys() {
            List<AttributeDTO> attribList = List.of(
                buildAttributeDTO("", "test_value"),
                buildAttributeDTO("test_attrib-2", "another_value"),
                buildAttributeDTO("", "dead value"),
                buildAttributeDTO("test_attrib-3", "a third value"),
                buildAttributeDTO("", "also discarded"));

            Map<String, String> attribMap = Util.toMap(attribList);

            assertNotNull(attribMap);
            assertEquals(2, attribMap.size());

            for (AttributeDTO attrib : attribList) {
                if (attrib.getName().isEmpty()) {
                    continue;
                }

                assertTrue(attribMap.containsKey(attrib.getName()));
                assertEquals(attrib.getValue(), attribMap.get(attrib.getName()));
            }
        }

        @Test
        public void testToMapDiscardsAttributesWithNullKeys() {
            List<AttributeDTO> attribList = List.of(
                buildAttributeDTO(null, "test_value"),
                buildAttributeDTO("test_attrib-2", "another_value"),
                buildAttributeDTO(null, "dead value"),
                buildAttributeDTO("test_attrib-3", "a third value"),
                buildAttributeDTO(null, "also discarded"));

            Map<String, String> attribMap = Util.toMap(attribList);

            assertNotNull(attribMap);
            assertEquals(2, attribMap.size());

            for (AttributeDTO attrib : attribList) {
                if (attrib.getName() == null) {
                    continue;
                }

                assertTrue(attribMap.containsKey(attrib.getName()));
                assertEquals(attrib.getValue(), attribMap.get(attrib.getName()));
            }
        }

        @Test
        public void testToMapDiscardsAttributesWithNullValues() {
            List<AttributeDTO> attribList = List.of(
                buildAttributeDTO("test_attrib-1", "value 1"),
                buildAttributeDTO("test_attrib-2", null),
                buildAttributeDTO("test_attrib-3", "value 3"),
                buildAttributeDTO("test_attrib-4", null),
                buildAttributeDTO("test_attrib-5", "value 5"));

            Map<String, String> attribMap = Util.toMap(attribList);

            assertNotNull(attribMap);
            assertEquals(3, attribMap.size());

            for (AttributeDTO attrib : attribList) {
                if (attrib.getValue() != null) {
                    assertTrue(attribMap.containsKey(attrib.getName()));
                    assertEquals(attrib.getValue(), attribMap.get(attrib.getName()));
                }
                else {
                    assertFalse(attribMap.containsKey(attrib.getName()));
                }
            }
        }
    }

    @Test
    void shouldStripPrefix() {
        String text = "candlepin.async.jobs.org.candlepin.schedule";
        String result = Util.stripPrefix(text, "candlepin.async.jobs.");

        assertEquals("org.candlepin.schedule", result);
    }

    @Test
    void shouldStripPrefixIfPresent() {
        String text = "candlepin.async.jobs.org.candlepin.schedule";
        String result = Util.stripPrefix(text, "org.candlepin.async.jobs.");

        assertEquals(text, result);
    }

    @Test
    void testIsFalse() {
        assertEquals(true, Util.isFalse(null));
        assertEquals(true, Util.isFalse(false));
        assertEquals(false, Util.isFalse(true));
    }

    @Nested
    class ListsAreEqualTests {
        @Test
        public void testListsAreEqual() {
            List<String> ulist = List.of("a", "b", "c");
            ArrayList<String> arrlist = new ArrayList<>(ulist);
            LinkedList<String> llist = new LinkedList<>(ulist);

            assertTrue(Util.listsAreEqual(null, null, String::equals));
            assertTrue(Util.listsAreEqual(ulist, ulist, String::equals));
            assertTrue(Util.listsAreEqual(ulist, arrlist, String::equals));
            assertTrue(Util.listsAreEqual(ulist, llist, String::equals));
            assertTrue(Util.listsAreEqual(arrlist, arrlist, String::equals));
            assertTrue(Util.listsAreEqual(arrlist, llist, String::equals));
            assertTrue(Util.listsAreEqual(llist, llist, String::equals));
        }

        @Test
        public void checksElementCount() {
            List<String> list1 = List.of("a", "b", "c");
            List<String> list2 = List.of("a", "b", "c", "d");

            assertFalse(Util.listsAreEqual(list1, list2, String::equals));
            assertFalse(Util.listsAreEqual(list2, list1, String::equals));
        }

        @Test
        public void considersDuplicateElements() {
            List<String> list1 = List.of("a", "b", "b", "c");
            List<String> list2 = List.of("a", "b", "c", "c");

            assertFalse(Util.listsAreEqual(list1, list2, String::equals));
            assertFalse(Util.listsAreEqual(list2, list1, String::equals));
        }

        @Test
        public void considersElementOrder() {
            List<String> list1 = List.of("a", "b", "c");
            List<String> list2 = List.of("c", "b", "a");

            assertFalse(Util.listsAreEqual(list1, list2, String::equals));
            assertFalse(Util.listsAreEqual(list2, list1, String::equals));
        }

        @Test
        public void checksNullElementsForEquality() {
            List<String> list1 = Arrays.asList("a", null, "c");
            List<String> list2 = Arrays.asList("a", "b", "c");

            assertFalse(Util.listsAreEqual(list1, list2, String::equals));
            assertFalse(Util.listsAreEqual(list2, list1, String::equals));
        }

        @Test
        public void checksForNullLists() {
            List<String> list1 = List.of("a", "b", "c");

            assertFalse(Util.listsAreEqual(list1, null, String::equals));
            assertFalse(Util.listsAreEqual(null, list1, String::equals));
        }

        @Test
        public void considersNullElements() {
            List<String> list1 = Arrays.asList("a", null, "c");
            List<String> list2 = Arrays.asList("a", null, "c");

            assertTrue(Util.listsAreEqual(list1, list2, String::equals));
            assertTrue(Util.listsAreEqual(list2, list1, String::equals));
        }

        @Test
        public void requiresElementComparator() {
            List<String> list1 = List.of("a", "b", "c");
            List<String> list2 = List.of("1", "2", "3");

            assertThrows(IllegalArgumentException.class, () -> Util.listsAreEqual(list1, list2, null));
        }
    }

}
