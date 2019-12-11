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
package org.candlepin.common.config;

import org.apache.commons.lang.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * PropertyConverter. Inspired by
 * org.apache.commons.configuration.PropertyConverter.
 */
public class PropertyConverter {
    private static Logger log = LoggerFactory.getLogger(PropertyConverter.class);

    private static final String HEX_PREFIX = "0x";
    private static final int HEX_RADIX = 16;

    private static final String BIN_PREFIX = "0b";
    private static final int BIN_RADIX = 2;

    /** Constant for the argument classes of the Number constructor that takes a String. */
    private static final Class<?>[] CONSTR_ARGS = {String.class};

    protected static final String ERROR_MESSAGE = "The value %s can not be converted to a %s object.";

    private PropertyConverter() {
        // This class only provides static methods.
    }

    private static String formatErrorMessage(Object value, Class<?> clazz) {
        return String.format(ERROR_MESSAGE, value, clazz.getName());
    }

    public static Boolean toBoolean(Object value) throws ConversionException {
        if (value == null) {
            return false;
        }

        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        else if (value instanceof String) {
            if ("1".equalsIgnoreCase((String) value) || "y".equalsIgnoreCase((String) value)) {
                return true;
            }

            return BooleanUtils.toBoolean((String) value);
        }
        else {
            String msg = formatErrorMessage(value, Boolean.class);
            log.warn(msg);
            throw new ConversionException(msg);
        }
    }

    public static Integer toInteger(Object value) throws ConversionException {
        Number n = toNumber(value, Integer.class);
        if (n instanceof Integer) {
            return (Integer) n;
        }
        else {
            return new Integer(n.intValue());
        }
    }

    public static Long toLong(Object value) throws ConversionException {
        Number n = toNumber(value, Long.class);
        if (n instanceof Long) {
            return (Long) n;
        }
        else {
            return new Long(n.longValue());
        }
    }

    public static BigInteger toBigInteger(Object value) throws ConversionException {
        Number n = toNumber(value, BigInteger.class);
        if (n instanceof BigInteger) {
            return (BigInteger) n;
        }
        else {
            return BigInteger.valueOf(n.longValue());
        }
    }

    protected static Number toNumber(Object value, Class<?> clazz) throws ConversionException {
        if (value instanceof Number) {
            return (Number) value;
        }
        else {
            String str = value.toString();
            if (str.startsWith(HEX_PREFIX)) {
                try {
                    return new BigInteger(str.substring(HEX_PREFIX.length()), HEX_RADIX);
                }
                catch (NumberFormatException nex) {
                    String msg = formatErrorMessage(value, clazz);
                    log.warn(msg);
                    throw new ConversionException(msg, nex);
                }
            }

            if (str.startsWith(BIN_PREFIX)) {
                try {
                    return new BigInteger(str.substring(BIN_PREFIX.length()), BIN_RADIX);
                }
                catch (NumberFormatException nex) {
                    String msg = formatErrorMessage(value, clazz);
                    log.warn(msg);
                    throw new ConversionException(msg, nex);
                }
            }

            try {
                Constructor<?> constr = clazz.getConstructor(CONSTR_ARGS);
                return (Number) constr.newInstance(new Object[] { str });
            }
            catch (Exception ex) {
                // Treat all possible exceptions the same way
                String msg = formatErrorMessage(value, clazz);
                log.warn(msg);
                throw new ConversionException(msg, ex);
            }
        }
    }

    public static List<String> toList(Object value) {
        if (value instanceof String) {
            String[] parts = ((String) value).trim().split("\\s*,\\s*");
            return Arrays.asList(parts);
        }
        else {
            String msg = formatErrorMessage(value, List.class);
            log.warn(msg);
            throw new ConversionException(msg);
        }
    }

    public static Set<String> toSet(Object value) {
        if (value instanceof String) {
            String[] parts = ((String) value).trim().split("\\s*,\\s*");
            return new HashSet<>(Arrays.asList(parts));
        }
        else {
            String msg = formatErrorMessage(value, Set.class);
            log.warn(msg);
            throw new ConversionException(msg);
        }
    }
}
