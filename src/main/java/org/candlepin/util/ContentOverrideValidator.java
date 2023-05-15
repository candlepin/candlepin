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
package org.candlepin.util;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.dto.api.server.v1.ContentOverrideDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.ContentOverride;

import org.xnap.commons.i18n.I18n;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;



/**
 * ContentOverrideValidator utility class used to validate
 * ContentOverride and its subclasses.  Includes column length
 * checking and overrideRules to check against blocklisted
 * name overrides
 */
public class ContentOverrideValidator {

    public static final Set<String> DEFAULT_BLOCKLIST = Set.of("", "name", "label");
    public static final Set<String> HOSTED_BLOCKLIST = Set.of("", "name", "label", "baseurl");

    protected final Set<String> blocklist;

    protected Configuration config;
    protected I18n i18n;

    @Inject
    public ContentOverrideValidator(Configuration config, I18n i18n) {
        this.config = config;
        this.i18n = i18n;

        if (config.getBoolean(ConfigProperties.STANDALONE)) {
            this.blocklist = DEFAULT_BLOCKLIST;
        }
        else {
            this.blocklist = HOSTED_BLOCKLIST;
        }
    }

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
    @SuppressWarnings("checkstyle:JavadocMethod")
    public void validate(Collection<? extends ContentOverrideDTO> overrides) {
        if (overrides != null) {
            Set<String> invalidLabels = new HashSet<>();
            Set<String> invalidProps = new HashSet<>();
            Set<String> invalidValues = new HashSet<>();
            Set<String> invalidLengthValues = new HashSet<>();

            for (ContentOverrideDTO override : overrides) {
                if  (override != null) {
                    String label = override.getContentLabel();
                    String name = override.getName();
                    String value = override.getValue();

                    if (label == null || label.length() == 0 ||
                        label.length() > ContentOverride.MAX_NAME_AND_LABEL_LENGTH) {
                        invalidLabels.add(label != null ? label : "null");
                    }

                    if (name == null || this.blocklist.contains(name.toLowerCase()) ||
                        name.length() > ContentOverride.MAX_NAME_AND_LABEL_LENGTH) {

                        invalidProps.add(name != null ? name : "null");
                    }

                    if (value == null || value.length() == 0) {
                        invalidValues.add(value != null ? value : "null");
                    }

                    if (value != null && value.length() > ContentOverride.MAX_VALUE_LENGTH) {
                        invalidLengthValues.add(name != null ? name : "null");
                    }
                }
            }

            if (!invalidLabels.isEmpty() || !invalidProps.isEmpty() || !invalidValues.isEmpty() ||
                !invalidLengthValues.isEmpty()) {
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

                if (!invalidLengthValues.isEmpty()) {
                    if (builder.length() > 0) {
                        builder.append('\n');
                    }

                    builder.append(i18n.tr("The following overrides have values longer than the " +
                        "maximum length of {0}: {1}",
                        ContentOverride.MAX_VALUE_LENGTH,
                        String.join(", ", invalidLengthValues)));
                }

                throw new BadRequestException(builder.toString());
            }
        }
    }
}
