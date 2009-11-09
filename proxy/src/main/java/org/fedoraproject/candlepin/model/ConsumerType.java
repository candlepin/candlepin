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

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Represents the type of consumer.
 * 
 * TODO: Examples?
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name="cp_consumer_type")
public class ConsumerType extends BaseModel implements Serializable {

    private String label;

    /**
     * default noarg ctor
     */
    public ConsumerType() {
        label = null;
    }

    /**
     * ConsumerType constructor with label
     * @param labelIn to set
     */
    public ConsumerType(String labelIn) {
        super(BaseModel.generateUUID());
        setLabel(labelIn);
    }
    
    /**
     * @return Returns the label.
     */
    @Id
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

    
}
