/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.List;
import java.util.Set;

@ConfigMapping
public interface AttributeValidatorConfig {

    @WithName("candlepin.integer_attributes")
    Set<String> intAttributes();

    @WithName("candlepin.positive_integer_attributes")
    Set<String> positiveIntAttributes();

    @WithName("candlepin.long_attributes")
    Set<String> longAttributes();

    @WithName("candlepin.positive_long_attributes")
    Set<String> positiveLongAttributes();

    @WithName("candlepin.boolean_attributes")
    Set<String> boolAttributes();

}
