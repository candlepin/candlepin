/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.util;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections.Closure;
import org.apache.commons.collections.ClosureUtils;
import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.DEROctetString;
import org.fedoraproject.candlepin.model.CuratorException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.security.cert.X509Extension;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
    private static Logger log = Logger.getLogger(Util.class);

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
    
    public static String decodeValue(byte[] value) {
        ASN1InputStream vis = null;
        ASN1InputStream decoded = null;
        try {
            vis = new ASN1InputStream(value);
            decoded = new ASN1InputStream(
                ((DEROctetString) vis.readObject()).getOctets());

            return decoded.readObject().toString();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        finally {
            if (vis != null) {
                try {
                    vis.close();
                }
                catch (IOException e) {
                    log.warn("failed to close ASN1 stream", e);
                }
            }

            if (decoded != null) {
                try {
                    decoded.close();
                }
                catch (IOException e) {
                    log.warn("failed to close ASN1 stream", e);
                }
            }
        }
    }
    
    public static String getValue(X509Extension cert, String extension) {
        return decodeValue(cert.getExtensionValue(extension));
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
            log.warn( msg + ".close() was not successful!", e);
        }
    }
    
    public static String capitalize(String str) {
        char [] chars = str.toCharArray();
        chars[0] = Character.toUpperCase(chars[0]); 
        return new String(chars);
    }
    
    public static long generateUniqueLong() {
        String id = "";
        try {
            long time = System.currentTimeMillis();
            byte[] ipaddress = Inet4Address.getLocalHost().getAddress();
            Random random = new SecureRandom();
            id = id + (long) ipaddress[3] + time + random.nextInt(255);
        }
        catch (UnknownHostException ex) {
            ex.printStackTrace();
        }
        return Math.abs(Long.valueOf(id));
    }
    
    public static String toBase64(byte [] data) {
        try {
            return new String(Base64.encodeBase64(data), "ASCII");
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
    

    
}
