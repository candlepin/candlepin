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
package org.candlepin.model;

import org.candlepin.model.dto.CandlepinDTO;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.MappedSuperclass;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.xml.bind.annotation.XmlType;



/**
 * Abstract class for hibernate entities
 *
 * @param <T>
 *  Entity type extending this class; should be the name of the subclass
 */
@MappedSuperclass
@XmlType(name = "CandlepinObject")
@JsonFilter("DefaultFilter")
public abstract class AbstractHibernateObject<T extends AbstractHibernateObject>
    implements Persisted, Serializable, TimestampedEntity<T> {

    private static final long serialVersionUID = 6677558844288404862L;

    public static final String DEFAULT_SORT_FIELD = "created";

    private Date created;
    private Date updated;

    @PrePersist
    protected void onCreate() {
        Date now = new Date();

        if (this.created == null) {
            setCreated(now);
        }

        setUpdated(now);
    }

    @PreUpdate
    protected void onUpdate() {
        setUpdated(new Date());
    }

    @JsonInclude(Include.NON_NULL)
    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    @JsonInclude(Include.NON_NULL)
    public Date getUpdated() {
        return updated;
    }

    public void setUpdated(Date updated) {
        this.updated = updated;
    }

    /**
     * Populates this entity with the data contained in the source DTO. Unpopulated values within
     * the DTO will be ignored.
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
    public AbstractHibernateObject populate(CandlepinDTO source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (source.getCreated() != null) {
            this.setCreated(source.getCreated());
        }

        if (source.getUpdated() != null) {
            this.setUpdated(source.getUpdated());
        }

        return this;
    }
}
