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

package org.fedoraproject.candlepin.model;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;

import org.hibernate.annotations.ForeignKey;

/**
 * Attributes can be thought of as a hint on some restriction on the usage of an
 * entitlement. They will not actually contain the logic on how to enforce the
 * Attribute, but basically just act as a constant the policy rules can look
 * for. Attributes may also be used to carry more complex JSON data specific to
 * a particular deployment of Candlepin.
 *
 * Attributes are used by both Products and Entitlement Pools.
 */
@Entity
//@Table(name = "cp_attribute")
@SequenceGenerator(name = "seq_attribute", sequenceName = "seq_attribute",
        allocationSize = 1)
//@Embeddable
public class Attribute extends AbstractHibernateObject {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_attribute")
    protected Long id;
    
    @Column(nullable = false)
    protected String name;
    
//    @Column(nullable = false)
    @Column
    protected String value;

    @OneToMany(targetEntity = Attribute.class, cascade = CascadeType.ALL,
        fetch = FetchType.EAGER)
    @ForeignKey(name = "fk_attribute_parent_id",
            inverseName = "fk_attribute_child_id")
    @JoinTable(name = "cp_attribute_hierarchy",
        joinColumns = @JoinColumn(name = "PARENT_ATTRIBUTE_ID"),
        inverseJoinColumns = @JoinColumn(name = "CHILD_ATTRIBUTE_ID"))
    protected Set<Attribute> childAttributes;

    /**
     * default ctor
     */
    public Attribute() {

    }

    /**
     * @param name attribute name
     * @param quantity quantity of the attribute.
     */
    public Attribute(String name, String quantity) {
        this.name = name;
        this.value = quantity;
    }

    public String toString() {
        return "Attribute [id=" + id + ", name=" + name + ", value=" + value + "]";
    }
    
    public String getName() {
        return name;
    }
    
    public Long getId() {
        return this.id;
    }
    
    public void setId(Long id) {
        this.id = id;
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
            name.equals(another.getName()) &&
            value.equals(another.getValue());
    }
    
    @Override
    public int hashCode() {
        return name.hashCode() * 31 + value.hashCode();
    }

    // TODO: is this attribute hierarchy used at all?
    public Set<Attribute> getChildAttributes() {
        if (childAttributes == null) {
            childAttributes = new HashSet<Attribute>();
        }
        return childAttributes;
    }

    public void setChildAttributes(Set<Attribute> childAttributes) {
        this.childAttributes = childAttributes;
    }

    public void addChildAttribute(Attribute newChild) {
        if (this.childAttributes == null) {
            this.childAttributes = new HashSet<Attribute>();
        }
        this.childAttributes.add(newChild);
    }

    public void addChildAttribute(String key, String val) {
        addChildAttribute(new Attribute(key, val));
    }

    public Attribute getChildAttribute(String key) {
        for (Attribute a : childAttributes) {
            if (a.getName().equals(key)) {
                return a;
            }
        }
        return null;
    }

}
