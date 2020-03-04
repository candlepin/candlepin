/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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
package org.candlepin.resource.validation;

import com.google.inject.Inject;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;


/**
 * Provides utility methods that perform various types of validations on DTO objects.
 * Recommended usage: Use as early as possible in PUT/POST Resource endpoint methods to validate incoming
 * DTO objects.
 */
public class DTOValidator {

    private Validator validator;

    @Inject
    public DTOValidator(ValidatorFactory validatorFactory) {
        this.validator = validatorFactory.getValidator();
    }

    /**
     * Performs validation of the DTO's fields (and their fields, thus validating the whole DTO tree) based
     * on the {@link javax.validation.constraints} annotations set on the them (such as {@link NotNull} and
     *  {@link Size}).
     *
     * @param dto the DTO object to validate
     * @throws ConstraintViolationException when a constraint validation has failed
     */
    public void validateConstraints(Object dto) {
        Set<ConstraintViolation<Object>> violations = this.validator.validate(dto);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    /**
     * Accepts a variable amount of method references to getters that return Collections of elements, and
     * validates that none of the elements contained in them are null. When at least one element of any of
     * the Collections is null, an exception is thrown.
     *
     * If the collection returned by the getter itself is null, no exception is thrown (null collection
     * is considered valid).
     *
     * Usage example:
     * <pre>
     * {@code
     * validator.validateCollectionElementsNotNull(dto::getProductIds, dto::getEntitlements);
     * }
     * </pre>
     *
     * @param getters a variable amount of method references to getters that return Collections of items
     * @throws IllegalArgumentException when any of the collections returned from the specified getters
     * contains at least one null element
     */
    @SafeVarargs
    public final void validateCollectionElementsNotNull(Supplier<? extends Collection<?>> getter1,
        Supplier<? extends Collection<?>> getter2, Supplier<? extends Collection<?>> getter3,
        Supplier<? extends Collection<?>>... getters) {

        validateCollectionElementsNotNull(getter1, getter2, getter3);

        for (Supplier<? extends Collection<?>> getter : getters) {
            validateCollectionElementsNotNull(getter);
        }
    }

    /*
     * ======================================================================================================
     * The following methods are overloaded versions of validateCollectionElementsNotNull for one, two and
     * three getters respectively, as a performance optimization to avoid the implicit creation of an array
     * object that happens during the vararg method call. There are currently no DTOs that contain more than
     * three collections that require null element validation, so this optimisation would cover 100% of cases
     * for now, while allowing us to validate more than 3 collections at a time in the future.
     * ======================================================================================================
     */

    /**
     * Works the same as {@link #validateCollectionElementsNotNull(Supplier, Supplier, Supplier, Supplier...)}
     */
    public void validateCollectionElementsNotNull(Supplier<? extends Collection<?>> getter) {
        Collection<?> collection = getter.get();
        if (collection != null && collection.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("collection contains null elements");
        }
    }

    /**
     * Works the same as {@link #validateCollectionElementsNotNull(Supplier, Supplier, Supplier, Supplier...)}
     */
    public void validateCollectionElementsNotNull(Supplier<? extends Collection<?>> getter1,
        Supplier<? extends Collection<?>> getter2) {
        validateCollectionElementsNotNull(getter1);
        validateCollectionElementsNotNull(getter2);
    }

    /**
     * Works the same as {@link #validateCollectionElementsNotNull(Supplier, Supplier, Supplier, Supplier...)}
     */
    public void validateCollectionElementsNotNull(Supplier<? extends Collection<?>> getter1,
        Supplier<? extends Collection<?>> getter2, Supplier<? extends Collection<?>> getter3) {
        validateCollectionElementsNotNull(getter1);
        validateCollectionElementsNotNull(getter2);
        validateCollectionElementsNotNull(getter3);
    }


    /**
     * Accepts a variable amount of method references to getters that return Maps of elements, and
     * validates that none of the elements (keys and values) contained in them are null. When at least one
     * key or value of any of the Maps is null, an exception is thrown.
     *
     * If the map returned by the getter itself is null, no exception is thrown (null map is considered
     * valid).
     *
     * Usage example:
     * <pre>
     * {@code
     * validator.validateMapElementsNotNull(dto::getAttributes, dto::getProductAttributes);
     * }
     * </pre>
     *
     * @param getters a variable amount of method references to getters that return Maps of items
     * @throws IllegalArgumentException when any of the maps returned from the specified getters contains at
     * least one null key or value
     */
    @SafeVarargs
    public final void validateMapElementsNotNull(Supplier<? extends Map<?, ?>> getter1,
        Supplier<? extends Map<?, ?>> getter2, Supplier<? extends Map<?, ?>> getter3,
        Supplier<? extends Map<?, ?>>... getters) {

        validateMapElementsNotNull(getter1, getter2, getter3);

        for (Supplier<? extends Map<?, ?>> getter : getters) {
            validateMapElementsNotNull(getter);
        }
    }

    /*
     * ======================================================================================================
     * The following methods are overloaded versions of validateMapElementsNotNull for one, two and
     * three getters respectively, as a performance optimization to avoid the implicit creation of an array
     * object that happens during the vararg method call. There are currently no DTOs that contain more than
     * three maps that require null element validation, so this optimisation would cover 100% of cases
     * for now, while allowing us to validate more than 3 maps at a time in the future.
     * ======================================================================================================
     */

    /**
     * Works the same as {@link #validateMapElementsNotNull(Supplier, Supplier, Supplier, Supplier...)}
     */
    public void validateMapElementsNotNull(Supplier<? extends Map<?, ?>> getter) {
        Map<?, ?> map = getter.get();
        if (map != null &&
            (map.values().stream().anyMatch(Objects::isNull) ||
            map.keySet().stream().anyMatch(Objects::isNull))) {
            throw new IllegalArgumentException("map contains null elements");
        }
    }

    /**
     * Works the same as {@link #validateMapElementsNotNull(Supplier, Supplier, Supplier, Supplier...)}
     */
    public void validateMapElementsNotNull(Supplier<? extends Map<?, ?>> getter1,
        Supplier<? extends Map<?, ?>> getter2) {
        validateMapElementsNotNull(getter1);
        validateMapElementsNotNull(getter2);
    }

    /**
     * Works the same as {@link #validateMapElementsNotNull(Supplier, Supplier, Supplier, Supplier...)}
     */
    public void validateMapElementsNotNull(Supplier<? extends Map<?, ?>> getter1,
        Supplier<? extends Map<?, ?>> getter2, Supplier<? extends Map<?, ?>> getter3) {
        validateMapElementsNotNull(getter1);
        validateMapElementsNotNull(getter2);
        validateMapElementsNotNull(getter3);
    }
}
