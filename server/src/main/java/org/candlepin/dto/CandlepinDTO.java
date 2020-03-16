/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.dto;

import com.fasterxml.jackson.annotation.JsonFilter;

import java.io.Serializable;



/**
 * The CandlepinDTO is the base class for all DTOs, which provides common data and functionality.
 * <p></p>
 * DTO implementations should adhere to the following design rules, in no particular order:
 * <ul>
 *  <li>
 *    <strong>Collections must be fully encapsulated</strong><br/>
 *  </li><li>
 *    <strong>Collections returned by accessors must be views</strong><br/>
 *  </li><li>
 *    <strong>Join objects must have immutable references to their joined objects</strong><br/>
 *  </li><li>
 *    <strong>Equality checks and hashcode calculation must only use the primary identifier of any
 *    nested objects</strong><br/>
 *  </li><li>
 *    <strong>Setters should return a self-reference to allow method chaining</strong><br/>
 *  </li>
 * </ul>
 *
 * @param <T>
 *  DTO type extending this class; should be the name of the subclass
 */
@JsonFilter("LegacyDTOFilter")
public abstract class CandlepinDTO<T extends CandlepinDTO> implements Cloneable, Serializable {
    public static final long serialVersionUID = 1L;

    /**
     * Initializes a new CandlepinDTO instance with null values.
     */
    protected CandlepinDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new CandlepinDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public CandlepinDTO(T source) {
        this.populate(source);
    }

    // TODO:
    // If actual data or functionality is ever added to this class, we need to also add
    // implementations for the equals, hashCode and clone methods.

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract boolean equals(Object obj);

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract int hashCode();

    /**
     * Creates and returns a copy of this object, such that the following are true:
     * <tt>
     *  x.clone() != x
     *  x.clone().getClass() == x.getClass()
     *  x.clone().equals(x) && x.equals(x.clone())
     * </tt>
     * Further, any cloned instance will be a deep-copy of this instance. That is, changes made to
     * either the copy or the original will not be reflected in the other.
     * <p></p>
     * <strong>Note:</strong> Developers implementing the clone method on subclasses should be
     * aware of any potential issues that can occur when the clone method is not properly
     * implemented. The following URL(s) provide some details on some pitfalls associated with
     * cloning objects:
     * <ul>
     *  <li>http://jtechies.blogspot.com/2012/07/item-11-override-clone-judiciously.html</li>
     * </ul>
     *
     * @return
     *  a copy of this DTO instance
     */
    @Override
    public T clone() {
        T copy;

        try {
            copy = (T) super.clone();
        }
        catch (CloneNotSupportedException e) {
            // This should never happen.
            throw new RuntimeException("Clone not supported", e);
        }

        return copy;
    }

    /**
     * Populates this DTO instance with data contained in the source DTO. This method, in effect,
     * turns this instance into a shallow copy of the source instance; with changes made to any
     * nested objects in one object reflected in the other.
     * <p></p>
     * Subclasses should ensure that the populate method of the super class is called as the first
     * step in their implementations, both to reduce the amount of boilerplate code with repeated
     * checks and assignments, and to ensure the super class has a chance to copy any private data
     * that cannot be accessed by the subclass.
     *
     * @param source
     *  The source object to use to populate this object
     *
     * @throws IllegalArgumentException
     *  if source is null
     *
     * @return
     *  this CandlepinDTO instance
     */
    public T populate(T source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        // Intentionally left empty; subclasses will expand on this as necessary.

        return (T) this;
    }
}
