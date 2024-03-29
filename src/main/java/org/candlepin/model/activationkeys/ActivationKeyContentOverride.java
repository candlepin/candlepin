/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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
import javax.validation.constraints.NotNull;



/**
 * ActivationKeyContentOverride
 */
@Entity
@DiscriminatorValue("activation_key")
public class ActivationKeyContentOverride extends
    ContentOverride<ActivationKeyContentOverride, ActivationKey> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    @NotNull
    private ActivationKey key;

    public ActivationKeyContentOverride() {
        // Intentionally left empty
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
    public ActivationKey getKey() {
        return key;
    }

    /**
     * @param key the activation key
     *
     * @return
     *  a reference to this content override
     */
    public ActivationKeyContentOverride setKey(ActivationKey key) {
        this.key = key;
        return this;
    }

    public ActivationKeyContentOverride setParent(ActivationKey parent) {
        this.setKey(parent);
        return this;
    }

    public ActivationKey getParent() {
        return this.getKey();
    }
}
