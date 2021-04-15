/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
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
    public void toDate() {
        Calendar c = Calendar.getInstance();
        c.setTime(Util.toDate("03/17/2011"));
        // apparently March is month 2 (since it's 0 based)
        assertEquals(2, c.get(Calendar.MONTH));
        assertEquals(17, c.get(Calendar.DAY_OF_MONTH));
        assertEquals(2011, c.get(Calendar.YEAR));
    }

    @Test
    public void toDateNull() {
        String date = null;
        assertThrows(NullPointerException.class, () -> Util.toDate(date));
    }

    @Test
    public void toDateShortForm() {
        Calendar c = Calendar.getInstance();
        c.setTime(Util.toDate("3/1/11"));
        // apparently March is month 2 (since it's 0 based)
        assertEquals(2, c.get(Calendar.MONTH));
        assertEquals(1, c.get(Calendar.DAY_OF_MONTH));
        assertEquals(11, c.get(Calendar.YEAR));
    }

    @Test
    public void toDateMilitaryForm() {
        assertThrows(RuntimeException.class, () -> Util.toDate("17 March 2011"));
    }

    @Test
    public void toDateStdLongForm() {
        assertThrows(RuntimeException.class, () -> Util.toDate("March 17, 2011"));
    }

    @Test
    public void equals() {
        assertTrue(Util.equals(null, null));
        assertTrue(Util.equals("foo", "foo"));
        assertFalse(Util.equals("foo", "bar"));
        assertFalse(Util.equals(null, "foo"));
    }

    @Test
    public void closeSafelyShouldAcceptNull() {
        // nothing to assert, if it doesn't throw an
        // exception we're good.
        TestClosable closable = mock(TestClosable.class);
        Util.closeSafely(null, "passing in null");
        verify(closable, never()).close();
        verify(mockapp, never()).doAppend(null);
    }

    @Test
    public void closeSafely() {
        ArgumentCaptor<LoggingEvent> message = ArgumentCaptor.forClass(LoggingEvent.class);
        TestClosable closable = mock(TestClosable.class);
        Util.closeSafely(closable, "objectname");
        verify(closable, atLeastOnce()).close();
        verify(mockapp, atLeastOnce()).doAppend(message.capture());
        assertEquals("Going to close: objectname", message.getValue().getMessage());
    }

    @Test
    public void closeSafelyWithException() {
        ArgumentCaptor<LoggingEvent> message = ArgumentCaptor.forClass(LoggingEvent.class);
        TestClosable closable = mock(TestClosable.class);
        doThrow(new RuntimeException("booyah")).when(closable).close();
        Util.closeSafely(closable, "objectname");
        verify(closable, atLeastOnce()).close();
        verify(mockapp, atLeastOnce()).doAppend(message.capture());
        assertEquals("objectname.close() was not successful!",
            message.getValue().getMessage());
    }

    @Test
    public void capitalize() {
        assertEquals("Abcde", Util.capitalize("abcde"));
        assertEquals("Abcde", Util.capitalize("Abcde"));
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
    public void json() throws JsonProcessingException {
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
    public void testGetHostname() {
        try {
            String hostname = Util.getHostname();
            assertNotNull(hostname);
        }
        catch (Exception e) {
            fail("getHostname should not throw an exception");
        }
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
}
