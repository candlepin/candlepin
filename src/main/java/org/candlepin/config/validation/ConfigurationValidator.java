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
package org.candlepin.config.validation;

import org.candlepin.config.Configuration;

import java.util.Set;

/**
 * The validator interface defines the API for validating a configuration.
 */
public interface ConfigurationValidator {

    /**
     * Validates the configuration based on the defined criteria and returns a list of failures. An empty
     * list indicates a successful validation.
     *
     * @param config
     *  used to retrieve configuration values based on keys
     *
     * @return
     *  a set of validation failures that include descriptive messages. An empty list indicates a successful
     *  validation. No failures will not return null.
     */
    Set<String> validate(Configuration config);
}

