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
package org.candlepin.util;

import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.dto.api.v1.ContentOverrideDTO;

import com.google.inject.Inject;

import org.xnap.commons.i18n.I18n;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;



/**
 * ContentOverrideValidator utility class used to validate
 * ContentOverride and its subclasses.  Includes column length
 * checking and overrideRules to check against blacklisted
 * name overrides
 */
public class ContentOverrideValidator {

    public static final int MAX_VALUE_LENGTH = 255;
    public static final Set<String> DEFAULT_BLACKLIST = Collections.<String>unmodifiableSet(
        new HashSet<String>(Arrays.asList("", "name", "label")));
    public static final Set<String> HOSTED_BLACKLIST = Collections.<String>unmodifiableSet(
        new HashSet<String>(Arrays.asList("", "name", "label", "baseurl")));


    protected final Set<String> blacklist;

    protected Configuration config;
    protected I18n i18n;

    @Inject
    public ContentOverrideValidator(Configuration config, I18n i18n) {
        this.config = config;
        this.i18n = i18n;

        if (config.getBoolean(ConfigProperties.STANDALONE, true)) {
            this.blacklist = DEFAULT_BLACKLIST;
        }
        else {
            this.blacklist = HOSTED_BLACKLIST;
        }
    }

    @SuppressWarnings("checkstyle:JavadocMethod")
    /**
     * Validates the given ContentOverrideDTO instances, checking that the overridden properties
     * aren't protected and both the property name and value are short enough to fit in the
     * database. If any of the overrides are invalid, this method throws an exception.
     *
     * @param overrides
     *  A collection of ContentOverrideDTO instances to validate
     *
     * @throws BadRequestException
     *  if the collection of overrides contains an invalid override
     */
    public void validate(Collection<? extends ContentOverrideDTO> overrides) {
        if (overrides != null) {
            Set<String> invalidLabels = new HashSet<>();
            Set<String> invalidProps = new HashSet<>();
            Set<String> invalidValues = new HashSet<>();

            for (ContentOverrideDTO override : overrides) {
                if  (override != null) {
                    String label = override.getContentLabel();
                    String name = override.getName();
                    String value = override.getValue();

                    if (label == null || label.length() == 0 || label.length() > MAX_VALUE_LENGTH) {
                        invalidLabels.add(label != null ? label : "null");
                    }

                    if (name == null || this.blacklist.contains(name.toLowerCase()) ||
                        name.length() > MAX_VALUE_LENGTH) {

                        invalidProps.add(name != null ? name : "null");
                    }

                    if (value == null || value.length() == 0 || value.length() > MAX_VALUE_LENGTH) {
                        invalidValues.add(value != null ? value : "null");
                    }
                }
            }

            if (!invalidLabels.isEmpty() || !invalidProps.isEmpty() || !invalidValues.isEmpty()) {
                StringBuilder builder = new StringBuilder();

                if (!invalidLabels.isEmpty()) {
                    builder.append(i18n.tr("The following content labels are invalid: {0}",
                        String.join(", ", invalidLabels)));
                }

                if (!invalidProps.isEmpty()) {
                    if (builder.length() > 0) {
                        builder.append('\n');
                    }

                    builder.append(i18n.tr("The following content properties cannot be overridden: {0}",
                        String.join(", ", invalidProps)));
                }

                if (!invalidValues.isEmpty()) {
                    if (builder.length() > 0) {
                        builder.append('\n');
                    }

                    builder.append(i18n.tr("The following override values are invalid: {0}",
                        String.join(", ", invalidValues)));
                }

                throw new BadRequestException(builder.toString());
            }
        }
    }
}
