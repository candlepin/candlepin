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
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlTransient;

import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Index;

/**
 * ConsumerContentOverride
 *
 * Represents an override to a value for a specific content set and named field.
 */
@Entity
@Table(name = "cp_consumer_content_override")
public class ConsumerContentOverride extends AbstractHibernateObject {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    private String id;

    @ManyToOne
    @ForeignKey(name = "fk_consumer_content_consumer")
    @JoinColumn(nullable = false)
    @Index(name = "cp_cnsmr_cntnt_cnsmr_fk_idx")
    private Consumer consumer;

    @Column(name = "content_label")
    private String contentLabel;

    private String name;

    private String value;

    public ConsumerContentOverride() {

    }

    public ConsumerContentOverride(Consumer consumer,
        String contentLabel, String name, String value) {
        this.setConsumer(consumer);
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

    public void setConsumer(Consumer consumer) {
        this.consumer = consumer;
    }

    @XmlTransient
    public Consumer getConsumer() {
        return consumer;
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
        result.append("[consumer=");
        result.append(consumer.getUuid());
        result.append(", ");
        result.append("content=");
        result.append(contentLabel);
        result.append(", ");
        result.append("name=");
        result.append(name);
        result.append(", ");
        result.append("value=");
        result.append(value);
        return result.toString();
    }

}
