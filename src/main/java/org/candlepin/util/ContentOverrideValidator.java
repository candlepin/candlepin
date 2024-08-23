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
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.inject.Inject;



/**
 * ContentOverrideValidator utility class used to validate
 * ContentOverride and its subclasses.  Includes column length
 * checking and overrideRules to check against blocklisted
 * name overrides
 */
public class ContentOverrideValidator {

    public static final Pattern FIELD_SPLITTER_REGEX = Pattern.compile("\\s*,\\s*");

    // Fields which can never be overridden
    public static final Set<String> DEFAULT_BLOCKLIST = Set.of("name", "label");

    protected final Configuration config;
    protected final I18n i18n;

    protected final Set<String> blocklist;

    @Inject
    public ContentOverrideValidator(Configuration config, I18n i18n) {
        this.config = config;
        this.i18n = i18n;

        this.blocklist = new HashSet<>(DEFAULT_BLOCKLIST);
        this.loadBlocklistConfig();
    }

    private void loadBlocklistConfig() {
        String cfgBlockList = this.config.getString(ConfigProperties.CONTENT_OVERRIDE_BLOCKLIST);
        String[] fields = FIELD_SPLITTER_REGEX.split(cfgBlockList);

        Stream.of(fields)
            .filter(Objects::nonNull)
            .filter(Predicate.not(String::isEmpty))
            .map(String::toLowerCase)
            .forEach(this.blocklist::add);
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

                    if (label == null || label.isBlank() ||
                        label.length() > ContentOverride.MAX_NAME_AND_LABEL_LENGTH) {

                        invalidLabels.add(label != null ? label : "null");
                    }

                    if (name == null || name.isBlank() || this.blocklist.contains(name.toLowerCase()) ||
                        name.length() > ContentOverride.MAX_NAME_AND_LABEL_LENGTH) {

                        invalidProps.add(name != null ? name : "null");
                    }

                    if (value == null || value.isEmpty()) {
                        invalidValues.add(value != null ? value : "null");
                    }

                    if (value != null && value.length() > ContentOverride.MAX_VALUE_LENGTH) {
                        invalidLengthValues.add(name != null ? name : "null");
                    }
                }
            }

            if (!invalidLabels.isEmpty() || !invalidProps.isEmpty() || !invalidValues.isEmpty() ||
                !invalidLengthValues.isEmpty()) {
                StringJoiner joiner = new StringJoiner("\n");

                if (!invalidLabels.isEmpty()) {
                    joiner.add(i18n.tr("The following content labels are invalid: {0}",
                        String.join(", ", invalidLabels)));
                }

                if (!invalidProps.isEmpty()) {
                    joiner.add(i18n.tr("The following content properties cannot be overridden: {0}",
                        String.join(", ", invalidProps)));
                }

                if (!invalidValues.isEmpty()) {
                    joiner.add(i18n.tr("The following override values are invalid: {0}",
                        String.join(", ", invalidValues)));
                }

                if (!invalidLengthValues.isEmpty()) {
                    joiner.add(i18n.tr("The following overrides have values longer than the " +
                        "maximum length of {0}: {1}",
                        ContentOverride.MAX_VALUE_LENGTH,
                        String.join(", ", invalidLengthValues)));
                }

                throw new BadRequestException(joiner.toString());
            }
        }
    }
}
