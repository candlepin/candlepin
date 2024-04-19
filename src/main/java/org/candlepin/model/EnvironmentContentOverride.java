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
package org.candlepin.model;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;



/**
 * A content override applied to an environment
 */
@Entity
@DiscriminatorValue("environment")
public class EnvironmentContentOverride extends ContentOverride<EnvironmentContentOverride, Environment> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    @NotNull
    private Environment environment;

    /**
     * Creates a new environment content override
     */
    public EnvironmentContentOverride() {
        // Intentionally left empty
    }

    /**
     * Fetches the environment this content override applies to. If the environment has not been
     * set, this method returns null.
     *
     * @return
     *  the environment to which this content override applies
     */
    public Environment getEnvironment() {
        return environment;
    }

    /**
     * Sets the environment to which this content override will apply. If the environment is null,
     * any previously set value will be cleared.
     *
     * @param environment
     *  the environment to which this content override should be applied, or null to clear any
     *  existing environment value
     *
     * @return
     *  a reference to this content override instance
     */
    public EnvironmentContentOverride setEnvironment(Environment environment) {
        this.environment = environment;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Environment getParent() {
        return this.getEnvironment();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        Environment environment = this.getEnvironment();

        return String.format("EnvironmentContentOverride [environment: %s, content: %s, name: %s, value: %s]",
            environment != null ? environment.getName() : null, this.getContentLabel(), this.getName(),
            this.getValue());
    }

}
