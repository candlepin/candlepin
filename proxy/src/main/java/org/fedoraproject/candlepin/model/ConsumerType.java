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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Represents the type of consumer.
 * 
 * See ProductFactory for some examples.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_consumer_type")
@SequenceGenerator(name = "seq_consumer_type", sequenceName = "seq_consumer_type", allocationSize = 1)
public class ConsumerType implements Persisted {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_consumer_type")
    private Long id;

    @Column(nullable = false, unique = true)
    private String label;
    
    // Constants
    public static final String SYSTEM = "system"; // physical system
    public static final String VIRT_SYSTEM = "virt_system"; 
    
    public ConsumerType() {
    }

    /**
     * ConsumerType constructor with label
     * @param labelIn to set
     */
    public ConsumerType(String labelIn) {
        this.label = labelIn;
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    /**
     * @return Returns the label.
     */
    public String getLabel() {
        return label;
    }
    
    /**
     * @param labelIn The label to set.
     */
    public void setLabel(String labelIn) {
        label = labelIn;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "ConsumerType [label=" + label + "]";
    }

    @Override
    public boolean equals(Object anObject) {
        if (this == anObject) return true;
        if (!(anObject instanceof ConsumerType)) return false;
        
        ConsumerType another = (ConsumerType) anObject;
        
        return 
            label.equals(another.getLabel());
    }
    
    @Override
    public int hashCode() {
        return label.hashCode();
    }
}
