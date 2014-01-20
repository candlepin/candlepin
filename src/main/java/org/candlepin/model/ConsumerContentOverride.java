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

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.xml.bind.annotation.XmlTransient;

import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.Index;

/**
 * ConsumerContentOverride
 *
 * Represents an override to a value for a specific content set and named field.
 */
@Entity
@DiscriminatorValue("consumer")
public class ConsumerContentOverride extends ContentOverride {

    @ManyToOne
    @ForeignKey(name = "fk_consumer_content_consumer")
    @JoinColumn(nullable = true)
    @Index(name = "cp_cnsmr_cntnt_cnsmr_fk_idx")
    private Consumer consumer;

    public ConsumerContentOverride() {

    }

    public ConsumerContentOverride(Consumer consumer,
        String contentLabel, String name, String value) {
        super(contentLabel, name, value);
        this.setConsumer(consumer);
    }

    public void setConsumer(Consumer consumer) {
        this.consumer = consumer;
    }

    @XmlTransient
    public Consumer getConsumer() {
        return consumer;
    }

    public String toString() {
        StringBuffer result = new StringBuffer();
        result.append("[consumer=");
        result.append(consumer.getUuid());
        result.append(", ");
        result.append("content=");
        result.append(this.getContentLabel());
        result.append(", ");
        result.append("name=");
        result.append(this.getName());
        result.append(", ");
        result.append("value=");
        result.append(this.getValue());
        return result.toString();
    }

}
