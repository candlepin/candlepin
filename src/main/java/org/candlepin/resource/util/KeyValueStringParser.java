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

import org.candlepin.exceptions.BadRequestException;

import org.apache.commons.lang3.tuple.Pair;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Provider;



/**
 * Provides utility functionality for parsing key-value strings received from clients.
 * <p></p>
 * The methods provided by this class are intended to be used by resource-level or user-facing
 * business methods. As such, they are very lenient on input, accepting null or empty collections,
 * and only raising exceptions when data is both present and malformed. In the case of an exception,
 * the exceptions are intended to be passed through to the user and contain both the appropriate
 * HTTP error code and translated error message. Use of these methods should not require
 * additional error handling or input validation in the general case.
 * <p></p>
 * Key-value strings are parsed according to the format "{key}:{value}", where only the first colon
 * is recognized as a delimiter, and all additional colons will be rolled into the value. If the
 * string is null, empty, or otherwise does not contain any instances of the delimiter, it is
 * considered malformed, and will cause methods in this class to throw a BadRequestException.
 */
public class KeyValueStringParser {
    /** Pattern to use for parsing key:value pairs; used to avoid repeatedly recompiling this regex. */
    private static final Pattern KVP_DELIMITER_PATTERN = Pattern.compile(":");

    private final Provider<I18n> i18nProvider;

    /**
     * Creates a new KeyValueStringParser which will use the specified i18n provider to fetch i18n
     * instances when translating error messages.
     *
     * @param i18nProvider
     *  an i18n provider to provide i18n instances for translation when an exception occurs; cannot
     *  be null
     *
     * @throws IllegalArgumentException
     *  if the given i18n provider is null
     */
    @Inject
    public KeyValueStringParser(Provider<I18n> i18nProvider) {
        if (i18nProvider == null) {
            throw new IllegalArgumentException("i18nProvider is null");
        }

        this.i18nProvider = i18nProvider;
    }

    /**
     * Creates a new KeyValueStringParser which will use the specified i18n instance for
     * translation.
     *
     * @param i18n
     *  the i18n instance to use for translation when an exception occurs; cannot be null
     *
     * @throws IllegalArgumentException
     *  if the given i18n instance is null
     */
    public KeyValueStringParser(I18n i18n) {
        if (i18n == null) {
            throw new IllegalArgumentException("i18n is null");
        }

        this.i18nProvider = () -> i18n;
    }

    /**
     * Utility method for consistent reproduction of a BadRequestException representing a format
     * exception for a given key-value pair entry.
     *
     * @param value
     *  the erroneous value for which to raise an exception
     *
     * @throws BadRequestException
     *  on all invocations of this method
     */
    private String throwFormatException(String value) {
        I18n i18n = this.i18nProvider.get();
        String errmsg = i18n.tr("Failed to parse parameter \"{0}\"; expected format: key:value", value);

        throw new BadRequestException(errmsg);
    }

    /**
     * Parses a single key-value pair string into a pair consisting of the key and value. If the
     * given string is null, or does not have contain the key and value delimiter, this method
     * throws an exception.
     *
     * @param kvpString
     *  a string containing a key and value delimited by a colon
     *
     * @throws BadRequestException
     *  if the given string is null, or does not contain the expected key-value delimiter
     *
     * @return
     *  a Pair object containing the key and value split on the first instance of the key-value
     *  delimiter
     */
    private Pair<String, String> parseString(String kvpString) {
        if (kvpString == null) {
            this.throwFormatException(null);
        }

        String[] parts = KVP_DELIMITER_PATTERN.split(kvpString,  2);
        if (parts.length < 2) {
            this.throwFormatException(kvpString);
        }

        return Pair.of(parts[0], parts[1]);
    }

    /**
     * Parses a single key-value string into a pair containing the parsed key and value. The output
     * of this method is wrapped in an Optional container instance to allow for fluent-style
     * processing of the pair without additional null checks.
     * <p></p>
     * This method will never return a null value, and will always return an Optional container
     * instance if it returns successfully. If the provided key-value string is null or empty,
     * this method returns an empty Optional. If the key-value string is malformed, this method
     * throws an exception.
     *
     * @param kvpString
     *  the key-value string to parse
     *
     * @throws BadRequestException
     *  if the key-value string is malformed
     *
     * @return
     *  an Optional container instance potentially containing a Pair consisting of the parsed key
     *  and value from the given key-value string
     */
    public Optional<Pair<String, String>> parseKeyValuePair(String kvpString) {
        return kvpString != null && !kvpString.isEmpty() ?
            Optional.of(this.parseString(kvpString)) :
            Optional.empty();
    }

    /**
     * Maps the given stream of key-value strings to a stream of Pair objects containing the parsed
     * keys and values, split on the first instance of the key-value delimiter within each string.
     * <p></p>
     * This method will never return a null value, and will always return a stream instance if it
     * returns successfully. If the provided stream is null, this method returns an empty stream.
     * If the stream contains null, empty, or malformed key-value strings, an exception will be
     * thrown as the stream processes those values, rather than being thrown when this method is
     * called. If the stream's order is deterministic, that order will be retained by the stream
     * returned from this method.
     *
     * @param stream
     *  a stream of key-value strings to map
     *
     * @return
     *  a stream of pair objects consisting of the parsed key-value strings
     */
    public Stream<Pair<String, String>> parseKeyValuePairs(Stream<String> stream) {
        return stream != null ? stream.map(this::parseString) : Stream.empty();
    }

    /**
     * Parses the given collection of key-value strings into a list of Pair objects containing the
     * parsed keys and values, split on the first instance of the key-value delimiter within each
     * string.
     * <p></p>
     * This method will never return a null value, and will always return a mutable list if it
     * returns successfully. If the provided collection is null, this method returns an empty
     * list. If the collection contains null, empty, or malformed key-value strings, this method
     * throws an exception immediately. If the collection's order is deterministic, that order will
     * be retained in the list returned from this method.
     *
     * @param kvpStrings
     *  an iterable collection of key-value strings to parse
     *
     * @throws BadRequestException
     *  if the collection contains any null, empty, or malformed key-value strings
     *
     * @return
     *  a list of Pair objects containing the parsed keys and values from the provided collection
     *  of key-value strings
     */
    public List<Pair<String, String>> parseKeyValuePairs(Iterable<String> kvpStrings) {
        List<Pair<String, String>> output = new ArrayList<>();

        if (kvpStrings != null) {
            kvpStrings.forEach(kvpstr -> output.add(this.parseString(kvpstr)));
        }

        return output;
    }

    /**
     * Parses the given collection of key-value strings into a list of Pair objects containing the
     * parsed keys and values, split on the first instance of the key-value delimiter within each
     * string.
     * <p></p>
     * This method will never return a null value, and will always return a mutable list if it
     * returns successfully. If the provided collection is null, this method returns an empty
     * list. If the collection contains null, empty, or malformed key-value strings, this method
     * throws an exception immediately.
     *
     * @param kvpStrings
     *  an array of key-value strings to parse
     *
     * @throws BadRequestException
     *  if the collection contains any null, empty, or malformed key-value strings
     *
     * @return
     *  a list of Pair objects containing the keys and values parsed from the provided collection
     *  of key-value strings
     */
    public List<Pair<String, String>> parseKeyValuePairs(String... kvpStrings) {
        return this.parseKeyValuePairs(kvpStrings != null ? Arrays.asList(kvpStrings) : null);
    }

    /**
     * Parses and maps the given collection of key-value strings into a map of the parsed keys and
     * values, split on the first instance of the key-value delimiter within each string.
     * <p></p>
     * This method will never return a null value and will always return a mutable map if it
     * returns successfully. If the provided collection is null, this method returns an empty map.
     * If the collection contains null, empty, or malformed key-value strings, this method throws
     * an exception. The collection's order is not retained by the map returned from this method;
     * however, duplicate keys occurring later in the collection will overwrite any earlier values.
     *
     * @param kvpStrings
     *  an iterable collection of key-value strings to parse
     *
     * @throws BadRequestException
     *  if the collection contains any null, empty, or malformed key-value strings
     *
     * @return
     *  a map containing keys and values parsed from the provided collection of key-value strings
     */
    public Map<String, String> mapKeyValuePairs(Iterable<String> kvpStrings) {
        Map<String, String> output = new HashMap<>();

        if (kvpStrings != null) {
            for (String kvpstr : kvpStrings) {
                Pair<String, String> pair = this.parseString(kvpstr);
                output.put(pair.getKey(), pair.getValue());
            }
        }

        return output;
    }

    /**
     * Parses and maps the given collection of key-value strings into a map of the parsed keys and
     * values, split on the first instance of the key-value delimiter within each string.
     * <p></p>
     * This method will never return a null value and will always return a mutable map if it
     * returns successfully. If the provided collection is null, this method returns an empty map.
     * If the collection contains null, empty, or malformed key-value strings, this method throws
     * an exception. The collection's order is not retained by the map returned from this method;
     * however, duplicate keys occurring later in the collection will overwrite any earlier values.
     *
     * @param kvpStrings
     *  an array of key-value strings to parse
     *
     * @throws BadRequestException
     *  if the collection contains any null, empty, or malformed key-value strings
     *
     * @return
     *  a map containing keys and values parsed from the provided collection of key-value strings
     */
    public Map<String, String> mapKeyValuePairs(String... kvpStrings) {
        return this.mapKeyValuePairs(kvpStrings != null ? Arrays.asList(kvpStrings) : null);
    }

}
