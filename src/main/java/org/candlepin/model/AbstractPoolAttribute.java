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

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.commons.lang.builder.HashCodeBuilder;
import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Index;

/**
 * An abstract base class for Pool attributes.
 */
@MappedSuperclass
public abstract class AbstractPoolAttribute extends AbstractHibernateObject
    implements Attribute {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid2")
    @Column(length = 37)
    protected String id;

    @Column(nullable = false)
    protected String name;

    @Column
    protected String value;

    @ManyToOne
    /* The @ForeignKey annotation is only used by HBM2DDL.  Please note that
     * this particular annotation will create keys with the same name because
     * there are two classes that extend this abstract class.  This duplication
     * is okay for PostgreSQL but is a no-no for Oracle.
     */
    @ForeignKey(name = "fk_pool_id", inverseName = "fk_pool_attribute_id")
    @JoinColumn(nullable = false)
    @Index(name = "cp_poolattribute_pool_fk_idx")
    protected Pool pool;

    public AbstractPoolAttribute() {
    }

    public AbstractPoolAttribute(String name, String val) {
        this.name = name;
        this.value = val;
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @XmlTransient
    public Pool getPool() {
        return pool;
    }

    public void setPool(Pool pool) {
        this.pool = pool;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }
        if (!(anObject instanceof Attribute)) {
            return false;
        }

        Attribute another = (Attribute) anObject;

        return
            name.equals(another.getName()) && value.equals(another.getValue());
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 31).
            append(name).
            append(value).toHashCode();
    }
}
