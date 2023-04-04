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
package org.candlepin.model.activationkeys;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerContentOverride;
import org.candlepin.model.ContentOverride;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * ActivationKeyContentOverride
 */
@Entity
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@DiscriminatorValue("activation_key")
public class ActivationKeyContentOverride extends
    ContentOverride<ActivationKeyContentOverride, ActivationKey> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = true)
    private ActivationKey key;

    public ActivationKeyContentOverride() {
    }

    public ActivationKeyContentOverride(ActivationKey key, String contentLabel, String name, String value) {
        super(contentLabel, name, value);
        this.setKey(key);
    }

    /**
     * Builds a ConsumerContentOverride instance from this activation key content override. The
     * returned content override will be populated with data present in this override at call time,
     * and will not reflect later changes made to this override.
     *
     * @param consumer
     *  the consumer for which the content override should be built
     *
     * @return
     *  a ConsumerContentOverride instance built from data contained in this content override
     */
    public ConsumerContentOverride buildConsumerContentOverride(Consumer consumer) {
        return new ConsumerContentOverride()
            .setConsumer(consumer)
            .setContentLabel(this.getContentLabel())
            .setName(this.getName())
            .setValue(this.getValue());
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

    public ActivationKeyContentOverride setParent(ActivationKey parent) {
        this.setKey(parent);
        return this;
    }

    public ActivationKey getParent() {
        return this.getKey();
    }
}
