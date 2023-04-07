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
package org.candlepin.spec.bootstrap.data.util;

import java.util.Random;

public final class StringUtil {

    private static final Random RANDOM = new Random();

    /** Default length of randomly generated strings */
    public static final int DEFAULT_RANDOM_STRING_LENGTH = 8;

    /** Character set consisting of upper- and lower-case alphabetical ASCII characters */
    public static final String CHARSET_ALPHABETICAL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    /** Character set consisting of numeric ASCII characters */
    public static final String CHARSET_NUMERIC = "0123456789";

    /** Character set consisting of characters representing base-16 (hexidecimal) values (0-9, A-F) */
    public static final String CHARSET_NUMERIC_HEX = "0123456789ABCDEF";

    /** Character set consisting of numeric and upper- and lower-case alphabetical ASCII characters */
    public static final String CHARSET_ALPHANUMERIC = CHARSET_ALPHABETICAL + CHARSET_NUMERIC;

    private StringUtil() {
        throw new UnsupportedOperationException();
    }

    /**
     * Generates a random string of characters from the specified charset string.
     *
     * @param length
     *  the length of the string to generate; must be a positive integer
     *
     * @param charset
     *  a string representing the character set to use for generating the string
     *
     * @throws IllegalArgumentException
     *  if length is a non-positive integer, or charset is null or empty
     *
     * @return
     *  a randomly generated string using the characters from the specified charset string
     */
    public static String random(int length, String charset) {
        if (length < 1) {
            throw new IllegalArgumentException("length is a non-positive integer");
        }

        if (charset == null || charset.isEmpty()) {
            throw new IllegalArgumentException("charset is null or empty");
        }

        return RANDOM.ints(length, 0, charset.length())
            .map(charset::charAt)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();
    }

    /**
     * Generates a string of characters from the specified charset string, appended to the given
     * prefix string. If the prefix is null or empty, the resultant string will only contain the
     * generated portion.
     *
     * @param prefix
     *  the prefix to prepend to the generated string
     *
     * @param length
     *  the length of the string to generate, not counting the prefix; must be a positive integer
     *
     * @param charset
     *  a string representing the character set to use for generating the string
     *
     * @throws IllegalArgumentException
     *  if length is a non-positive integer, or charset is null or empty
     *
     * @return
     *  a randomly generated string using the characters from the specified charset string appended
     *  to the provided prefix
     */
    public static String random(String prefix, int length, String charset) {
        String suffix = random(length, charset);

        return (prefix != null && !prefix.isEmpty()) ?
            prefix + suffix :
            suffix;
    }

    /**
     * Generates a random alphanumeric string of the specified length.
     *
     * @param length
     *  the length of the string to generate; must be a positive integer
     *
     * @throws IllegalArgumentException
     *  if length is a non-positive integer
     *
     * @return
     *  a randomly generated, alphanumeric string of the given length
     */
    public static String random(int length) {
        return random(length, CHARSET_ALPHANUMERIC);
    }

    /**
     * Generates a random, base-16, numeric string, eight characters in length, appended to the
     * given prefix string. If the prefix is null or empty, the resultant string will only contain
     * the generated portion.
     *
     * @param prefix
     *  the prefix to prepend to the generated string
     *
     * @return
     *  an eight-character, randomly generated, base-16 numeric string appended to the provided
     *  prefix
     */
    public static String random(String prefix) {
        return random(prefix, DEFAULT_RANDOM_STRING_LENGTH, CHARSET_NUMERIC_HEX);
    }

}
