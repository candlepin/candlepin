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

import java.io.IOException;
import java.math.BigInteger;
import java.security.cert.X509Extension;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.collections.Transformer;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.DEROctetString;
import org.fedoraproject.candlepin.exceptions.BadRequestException;

/**
 * Genuinely random utilities.
 */
public class Util {

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
    

    public static BigInteger toBigInt(long l) {
        return new BigInteger(String.valueOf(l));
    }
    
    public static String decodeValue(byte[] value) {
        try {
            return new ASN1InputStream(((DEROctetString) new ASN1InputStream(value)
                .readObject()).getOctets()).readObject().toString();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
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
    
    public static Long assertLong(String value, String message) {
        Long returnValue = null;
        
        if (value != null) {
            try {
                returnValue = Long.parseLong(value);
            }
            catch (NumberFormatException e) {
                throw new BadRequestException(message);
            }
        }
        return returnValue;
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

    public static int safeHashCode(Object object, int hash) {
        return object == null ? hash : object.hashCode();
    }

    @SuppressWarnings("unchecked")
    public static <T> Set<T> transform(Set<?> set,
        Transformer t) {
        Set<T> result = Util.newSet();
        for (Iterator iterator = set.iterator(); iterator.hasNext();) {
            result.add((T) t.transform(iterator.next()));
        }
        return result;
    }
}
