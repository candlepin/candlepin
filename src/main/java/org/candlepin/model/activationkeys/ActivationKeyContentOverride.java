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
package org.candlepin.model.activationkeys;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerContentOverride;
import org.candlepin.model.ContentOverride;
import org.hibernate.annotations.ForeignKey;

/**
 * ActivationKeyContentOverride
 */
@Entity
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@DiscriminatorValue("activation_key")
public class ActivationKeyContentOverride extends ContentOverride {

    @ManyToOne
    @ForeignKey(name = "fk_content_override_key")
    @JoinColumn(nullable = true)
    private ActivationKey key;

    public ActivationKeyContentOverride() {
    }

    public ActivationKeyContentOverride(ActivationKey key,
            String contentLabel, String name, String value) {
        super(contentLabel, name, value);
        this.setKey(key);
    }

    /**
     * @param consumer for whom to create a ConsumerContentOverride
     * @return ConsumerContentOverride for the given consumer
     */
    public ConsumerContentOverride buildConsumerContentOverride(Consumer consumer) {
        return new ConsumerContentOverride(consumer,
            this.getContentLabel(), this.getName(), this.getValue());
    }

    /**
     * @return the parent activation key
     */
    @XmlTransient
    public ActivationKey getKey() {
        return key;
    }

    /**
     * @param key the activation key
     */
    public void setKey(ActivationKey key) {
        this.key = key;
    }
}
