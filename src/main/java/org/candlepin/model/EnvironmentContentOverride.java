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
package org.candlepin.model;

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
@DiscriminatorValue("environment")
public class EnvironmentContentOverride extends
    ContentOverride<EnvironmentContentOverride, Environment> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = true)
    private Environment environment;

    public EnvironmentContentOverride() {
    }

    public EnvironmentContentOverride(Environment environment, String contentLabel, String name,
        String value) {
        super(contentLabel, name, value);
        this.setEnvironment(environment);
    }

    /**
     * Builds a EnvironmentContentOverride instance from this activation key content override. The
     * returned content override will be populated with data present in this override at call time,
     * and will not reflect later changes made to this override.
     *
     * @param environment
     *  the consumer for which the content override should be built
     *
     * @return
     *  a EnvironmentContentOverride instance built from data contained in this content override
     */
    public EnvironmentContentOverride buildConsumerContentOverride(Environment environment) {
        return new EnvironmentContentOverride()
            .setEnvironment(environment)
            .setContentLabel(this.getContentLabel())
            .setName(this.getName())
            .setValue(this.getValue());
    }

    /**
     * @return the parent environment
     */
    @XmlTransient
    public Environment getEnvironment() {
        return environment;
    }

    /**
     * @param environment the environment
     *
     * @return the parent environment
     */
    public EnvironmentContentOverride setEnvironment(Environment environment) {
        this.environment = environment;
        return this;
    }

    public EnvironmentContentOverride setParent(Environment parent) {
        this.setEnvironment(parent);
        return this;
    }

    public Environment getParent() {
        return this.getEnvironment();
    }
}
