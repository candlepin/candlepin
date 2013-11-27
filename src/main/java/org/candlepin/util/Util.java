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

import org.candlepin.model.CuratorException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.collections.Closure;
import org.apache.commons.collections.ClosureUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Genuinely random utilities.
 */
public class Util {

    /**
     *
     */
    public static final String UTC_STR = "UTC";
    private static Logger log = LoggerFactory.getLogger(Util.class);
    private static ObjectMapper mapper = new ObjectMapper();

    private Util() {
        // default ctor
    }

    /**
     * Generates a random UUID.
     *
     * @return a random UUID.
     */
    public static String generateUUID() {
        return UUID.randomUUID().toString();
    }

    public static <T> List<T> subList(List<T> parentList, int start, int end) {
        List<T> l = new ArrayList<T>();
        for (int i = start; i < end; i++) {
            l.add(parentList.get(i));
        }
        return l;
    }

    public static <T> List<T> subList(List<T> parentList, int size) {
        return subList(parentList, 0, size - 1);
    }
    public static <E> List<E> newList() {
        return new ArrayList<E>();
    }

    public static <K, V> Map<K, V> newMap() {
        return new HashMap<K, V>();
    }

    public static <T> Set<T> newSet() {
        return new HashSet<T>();
    }


    public static Date getFutureDate(int years) {
        Calendar future = Calendar.getInstance();
        future.setTime(new Date());
        future.set(Calendar.YEAR, future.get(Calendar.YEAR) + years);
        return future.getTime();
    }

    public static Date tomorrow() {
        return addDaysToDt(1);
    }

    public static Date yesterday() {
        return addDaysToDt(-1);
    }

    public static Date addDaysToDt(int dayField) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, dayField);
        return calendar.getTime();
    }

    public static Date addMinutesToDt(int minuteField) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, minuteField);
        return calendar.getTime();
    }

    public static Date addToFields(int day, int month, int yr) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, day);
        calendar.add(Calendar.MONTH, month);
        calendar.add(Calendar.YEAR, yr);
        return calendar.getTime();
    }

    public static Date roundToMidnight(Date dt) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(dt);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        return cal.getTime();
    }

    public static BigInteger toBigInt(long l) {
        return new BigInteger(String.valueOf(l));
    }

    public static Date toDate(String dt) {
        SimpleDateFormat fmt = new SimpleDateFormat("MM/dd/yyyy");
        try {
            return fmt.parse(dt);
        }
        catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T assertNotNull(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    public static String defaultIfEmpty(String str, String def) {
        if (str == null || str.trim().length() == 0) {
            return def;
        }
        return str;
    }

    public static boolean equals(String str, String str1) {
        if (str == str1) {
            return true;
        }

        if ((str == null) ^ (str1 == null)) {
            return false;
        }

        return str.equals(str1);
    }

    private static Closure closeInvoker =
        ClosureUtils.invokerClosure("close");

    public static void closeSafely(Object closable, String msg) {
        if (closable == null) {
            return;
        }
        try {
            log.info("Going to close: " + msg);
            closeInvoker.execute(closable);
        }
        catch (Exception e) {
            log.warn(msg + ".close() was not successful!", e);
        }
    }

    public static String capitalize(String str) {
        char [] chars = str.toCharArray();
        chars[0] = Character.toUpperCase(chars[0]);
        return new String(chars);
    }

    public static long generateUniqueLong() {
        /*
          This deserves explanation.

          A random positive Long has 63 bits of hash space.  We want
          to have a given amount of certainty about the probability of
          collisions within this space.  This is an instance of the
          Birthday Problem[1].  We can get the probability that any
          two random numbers collide with the approximation:

          1-e**((-(N**2))/(2H))

          Where e is Euler's number, N is the number of random numbers
          generated, and H is the number of possible random outcomes.

          Suppose then that we generated one billion serials, with
          each serial being a 63-bit positive Long.  Then our
          probability of having a collision would be:

          irb(main):001:0> 1-Math.exp((-(1000000000.0**2))/(2.0*(2**63)))
          => 0.052766936243662

          So, if we generated a *billion* such serials, there is only
          a 5% chance that any two of them would be the same.  In
          other words, there is 95% chance that we would not have a
          single collision in one billion entries.

          The chances obviously get even less likely with smaller
          numbers.  With one million, the probability of a collision
          is:

          irb(main):002:0> 1-Math.exp((-(1000000.0**2))/(2.0*(2**63)))
          => 5.42101071809853e-08

          Or, 1 in 18,446,744.

          [1] http://en.wikipedia.org/wiki/Birthday_problem
         */

        return Math.abs(new SecureRandom().nextLong());
    }

    public static String toBase64(byte [] data) {
        try {
            // to be thread-safe, we should create it from the static method
            // If we don't specify the line separator, it will use CRLF
            return new String(new Base64(64, "\n".getBytes()).encode(data), "ASCII");
        }
        catch (UnsupportedEncodingException e) {
            log.warn("Unable to convert binary data to string", e);
            return new String(data);
        }
    }

    public static SimpleDateFormat getUTCDateFormat() {
        SimpleDateFormat iso8601DateFormat = new SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss'Z'");
        iso8601DateFormat.setTimeZone(TimeZone.getTimeZone(UTC_STR));
        return iso8601DateFormat;
    }

    public static String readFile(InputStream is) {
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader reader = new BufferedReader(isr);
        StringBuilder builder = new StringBuilder();
        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                builder.append(line + "\n");
            }
        }
        catch (IOException e) {
            throw new CuratorException(e);
        }
        finally {
            try {
                reader.close();
            }
            catch (IOException e) {
                log.warn("problem closing BufferedReader", e);
            }
        }
        return builder.toString();
    }

    public static String hash(String password) {
        //This is secure because even if the salt is known, a cracker
        //would still need to generate their own rainbow table, which
        //is the same as brute-forcing the password in the first place.

        String salt = "b669e3274a43f20769d3dedf03e9ac180e160f92";
        String combined = salt + password;

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1");
        }
        catch (NoSuchAlgorithmException nsae) {
            throw new RuntimeException(nsae);
        }

        try {
            md.update(combined.getBytes("UTF-8"), 0, combined.length());
        }
        catch (UnsupportedEncodingException uee) {
            throw new RuntimeException(uee);
        }

        byte[] sha1hash = md.digest();
        return new String(Hex.encodeHex(sha1hash));
    }

    public static String toJson(Object anObject) {
        String output = "";
        try {
            output = mapper.writeValueAsString(anObject);
        }
        catch (Exception e) {
            log.error("Could no serialize the object to json " + anObject, e);
        }
        return output;
    }

    public static Object fromJson(String json, Class clazz) {
        Object output = null;
        try {
            output = mapper.readValue(json, clazz);
        }
        catch (Exception e) {
            log.error("Could no de-serialize the following json " + json, e);
        }
        return output;
    }

    public static String getClassName(Class c) {
        return getClassName(c.getName());
    }

    public static String getClassName(String fullClassName) {
        int firstChar = fullClassName.lastIndexOf('.') + 1;
        if (firstChar > 0) {
            fullClassName = fullClassName.substring(firstChar);
        }
        return fullClassName;
    }

}
