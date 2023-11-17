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
package org.candlepin.paging;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;



/**
 * A field comparator factory which uses reflection against a specified class to build comparators
 * from arbitrary field names.
 *
 * @param <T>
 *  the class type for which this factory's mapping logic applies
 */
public class ReflectionFieldComparatorFactory<T> implements FieldComparatorFactory<T> {

    /** A collection of prefixes to apply when mapping field names to accessors */
    private static final List<String> METHOD_NAME_PREFIXES = List.of("get", "is", "has");

    private final Class<T> type;
    private final String defaultSortField;

    /**
     * Creates a new comparator factory for the given class type.
     *
     * @param type
     *  the class to use for mapping field names to functions
     *
     * @param defaultSortField
     *  the field name to attempt to map when building the default comparator
     *
     * @throws IllegalArgumentException
     *  if type is null
     */
    public ReflectionFieldComparatorFactory(Class<T> type, String defaultSortField) {
        if (type == null) {
            throw new IllegalArgumentException("type is null");
        }

        this.type = type;
        this.defaultSortField = defaultSortField;
    }

    /**
     * Builds a candidate method name from the given prefix and field name.
     * <p>
     * <strong>WARNING:</strong> This method performs no input validation! Only call from a trusted,
     * pre-validated context.
     *
     * @param prefix
     *  the prefix to apply to the field name
     *
     * @param fieldName
     *  the fieldName to use as the basis for the candidate method name
     *
     * @return
     *  a method name candidate using the given prefix and field name
     */
    private String buildMethodNameCandidate(String prefix, String fieldName) {
        StringBuilder builder = new StringBuilder(prefix)
            .append(fieldName.substring(0, 1).toUpperCase());

        if (fieldName.length() > 1) {
            builder.append(fieldName.substring(1));
        }

        return builder.toString();
    }

    /**
     * Gets the extractor function from the underlying type that best matches the given field name.
     * The extractor function must be a public method that requires zero parameters, have a return
     * types implementing the Comparable interface, and following the naming convention of
     * "[prefix][field_name]", where prefix is one of "get", "is", or "has", and "field_name" is the
     * given field name in title case. If a matching method cannot be found, this method returns null.
     * <p>
     * The field name may be specified in either camel case or title case, but should not include
     * the verb prefix. For example, the method "getProvidedProducts" can be successfully mapped
     * using the field name values "providedProducts" or "ProvidedProducts", but
     * <strong>not</strong> "providedproducts".
     *
     * @param fieldName
     *  the name of the field for which to generate an extractor function; case-sensitive. May be
     *  specified in either camel case or title case without spaces.
     *
     * @return
     *  an extractor function for the specified field name, or null if the field name could not be
     *  mapped to an accessor method on the underlying type
     */
    @SuppressWarnings("unchecked")
    private Function<? super T, Comparable<? super Comparable>> getExtractorFunction(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return null;
        }

        for (String prefix : METHOD_NAME_PREFIXES) {
            String candidate = this.buildMethodNameCandidate(prefix, fieldName);

            try {
                Method method = this.type.getMethod(candidate);
                Class<?> returnType = method.getReturnType();

                if (returnType == null || !Comparable.class.isAssignableFrom(returnType)) {
                    throw new NoSuchMethodException("incomparable return type: " + returnType);
                }

                return (T instance) -> {
                    try {
                        return (Comparable<? super Comparable>) method.invoke(instance);
                    }
                    catch (InvocationTargetException | IllegalAccessException e) {
                        throw new RuntimeException(e); // This shouldn't happen... probably.
                    }
                };
            }
            catch (NoSuchMethodException e) {
                // Intentionally left empty
            }
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Comparator<T> getComparator(String fieldName) {
        Function<? super T, Comparable<? super Comparable>> extractor = this.getExtractorFunction(fieldName);
        return extractor != null ? Comparator.comparing(extractor) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Comparator<T> getDefaultComparator() {
        return this.getComparator(this.defaultSortField);
    }

}
