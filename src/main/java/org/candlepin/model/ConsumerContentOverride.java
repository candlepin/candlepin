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

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlTransient;

import org.hibernate.annotations.GenericGenerator;

/**
 * ConsumerContentOverride
 */
@Entity
@Table(name = "cp_consumer_content_override")
public class ConsumerContentOverride extends AbstractHibernateObject {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    private String id;

    @Column(name = "consumer_id")
    private String consumerId;

    @Column(name = "content_label")
    private String contentLabel;

    private String name;

    private String value;

    public ConsumerContentOverride() {

    }

    public ConsumerContentOverride(String consumerId,
        String contentLabel, String name, String value) {
        this.setConsumerId(consumerId);
        this.setContentLabel(contentLabel);
        this.setName(name);
        this.setValue(value);
    }

    @XmlTransient
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setContentLabel(String contentLabel) {
        this.contentLabel = contentLabel;
    }

    public String getContentLabel() {
        return contentLabel;
    }

    public void setConsumerId(String consumerId) {
        this.consumerId = consumerId;
    }

    @XmlTransient
    public String getConsumerId() {
        return consumerId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @XmlTransient
    public Date getCreated() {
        return super.getCreated();
    }

    @XmlTransient
    public Date getUpdated() {
        return super.getUpdated();
    }

    public String toString() {
        StringBuffer result = new StringBuffer();
        result.append("ConsumerId: ");
        result.append(consumerId);
        result.append(", ");
        result.append("Content Label: ");
        result.append(contentLabel);
        result.append(", ");
        result.append("Name: ");
        result.append(name);
        result.append(", ");
        result.append("Value: ");
        result.append(value);
        return result.toString();
    }

}
