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
package org.candlepin.model;

import org.candlepin.model.dto.CandlepinDTO;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.MappedSuperclass;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;



/**
 * Abstract base class for Hibernate entities.
 * <p>
 * Due to the nature of model/entity classes and how Hibernate works internally, subclasses -- and
 * Hibernate entities in general -- should adhere to certain behavioral patterns to avoid problems
 * with the persistence engine, or a given subclass or proxy's ability to fetch accurate data.
 * <ul>
 *   <li>
 *   <strong>Avoid directly referencing an entity attribute directly outside of the constructors,
 *   accessors, and mutators</strong><br>
 *   Since entity classes tend to be non-final and are often fetched from Hibernate as proxy
 *   instances, doing so could lead to a case where a non-overloaded utility method could be
 *   operating on stale or invalid data, rather than the actual data the entity may contain.
 *   </li>
 *   <li>
 *   <strong>Fully encapsulate collections, and disallow external modification</strong><br>
 *   Similar to proxy objects, collections are often fetched as proxy objects and may have explicit
 *   behavior required for functionality in the persistence engine, such as cascades. By
 *   encapsulating such collections and ensuring modification is done through the class, certain
 *   issues with data consistency can be avoided.<br>
 *   Additionally, this avoids a class of issue surrounding validation or normalization being
 *   skipped due to direct modification of an entity's collection(s).
 *   </li>
 *   <li>
 *   <strong>Avoid non-default constructors as a requirement for instantiation</strong><br>
 *   Due to the high probability that entity classes will be subclasses by generated code, it is
 *   likely that parameterized constructors won't be called and cannot be relied upon for a global
 *   invocation.
 *   </li>
 * </ul>
 * As with all guidelines, there may be cases where one or more rules need to be violated for proper
 * functionality or more maintable code. In such cases, it should be documented in code at the very
 * least to explain why such a deviation is necessary and to avoid future maintenance pains or
 * confusion.
 *
 * @param <T>
 *  Entity type extending this class; should be the name of the subclass
 */
@MappedSuperclass
public abstract class AbstractHibernateObject<T extends AbstractHibernateObject>
    implements Persisted, Serializable, TimestampedEntity<T> {

    private static final long serialVersionUID = 6677558844288404862L;

    private Date created;
    private Date updated;

    /**
     * Performs data normalization when this entity is initially persisted to the underlying
     * persistence engine. This method is called automatically, and is not necessary to invoke
     * directly.
     * <p>
     * <strong>Note</strong>: subclasses overriding this method should ensure the parent
     * implementation is called by calling <tt>super.onCreate()</tt> at the head of the override.
     */
    @PrePersist
    protected void onCreate() {
        Date now = new Date();

        if (this.getCreated() == null) {
            this.setCreated(now);
        }

        this.setUpdated(now);
    }

    /**
     * Performs data normalization when changes to this entity are persisted to the underlying
     * persistence engine. This method is called automatically, and is not necessary to invoke
     * directly.
     * <p>
     * <strong>Note</strong>: subclasses overriding this method should ensure the parent
     * implementation is called by calling <tt>super.onUpdate()</tt> at the head of the override.
     */
    @PreUpdate
    protected void onUpdate() {
        this.setUpdated(new Date());
    }

    /**
     * Fetches the date this entity was created, generally indicating the time at which it was first
     * persisted to the database. If the entity has not yet been persisted, or the creation date has
     * not yet been set, this method returns null.
     *
     * @return
     *  the date this entity was initially persisted
     */
    public Date getCreated() {
        // TODO: FIXME: Change this and setCreated to use the java.time classes instead of Date
        return created;
    }

    /**
     * Sets or clears the creation date of this entity. If the given date is null, any existing
     * date will be cleared.
     * <p>
     * <strong>Note:</strong> this method will be called automatically on initial persist if the
     * creation date is not already explicitly set. It <strong>will not</strong> be called on
     * subsequent persists, even if the creation date is cleared.
     *
     * @param created
     *  the creation date to set for this entity, or null to clear the existing value
     *
     * @return
     *  a reference to this entity instance
     */
    public T setCreated(Date created) {
        this.created = created;
        return (T) this;
    }

    /**
     * Fetches the date this entity was last updated, generally indicating the time at which it was
     * last persisted to the database. If the entity has not yet been persisted or the last-update
     * date has not yet been set, this method returns null.
     *
     * @return
     *  the date this entity was last persisted
     */
    public Date getUpdated() {
        return updated;
    }

    /**
     * Sets or clears the last-update date of this entity. If the given date is null, any existing
     * last-updated date will be cleared.
     * <p>
     * <strong>Note:</strong> this method will be called automatically on persist, even if the
     * last-updated date is explicitly set.
     *
     * @param updated
     *  the last-updated date to set for this entity, or null to clear the existing value
     *
     * @return
     *  a reference to this entity instance
     */
    public T setUpdated(Date updated) {
        this.updated = updated;
        return (T) this;
    }

    /**
     * Populates this entity with the data contained in the source DTO. Unpopulated values within
     * the DTO will be ignored.
     *
     * @deprecated
     *  The DTO integrations have not been maintained and are no longer supported directly on the
     *  model, as it conflates the responsibilities of the DTO and model layers. Newer translation
     *  logic should occur on a case-by-case basis, or in a translation framework. This method will
     *  eventually be removed entirely.
     *
     * @param source
     *  The source DTO containing the data to use to update this entity
     *
     * @throws IllegalArgumentException
     *  if source is null
     *
     * @return
     *  A reference to this entity
     */
    @Deprecated
    public T populate(CandlepinDTO source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (source.getCreated() != null) {
            this.setCreated(source.getCreated());
        }

        if (source.getUpdated() != null) {
            this.setUpdated(source.getUpdated());
        }

        return (T) this;
    }
}
