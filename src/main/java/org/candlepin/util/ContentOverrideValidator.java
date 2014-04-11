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

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.ContentOverride;
import org.candlepin.policy.js.override.OverrideRules;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;

/**
 * ContentOverrideValidator utility class used to validate
 * ContentOverride and its subclasses.  Includes column length
 * checking and overrideRules to check against blacklisted
 * name overrides
 */
public class ContentOverrideValidator {

    private static final int MAX_COL_LENGTH = 255;
    private I18n i18n;
    private OverrideRules overrideRules;

    @Inject
    public ContentOverrideValidator(I18n i18n,
            OverrideRules overrideRules) {
        this.i18n = i18n;
        this.overrideRules = overrideRules;
    }

    public void validate(Collection<? extends ContentOverride> overrides) {
        Set<String> invalidOverrides = new HashSet<String>();
        for (ContentOverride override : overrides) {
            if (!overrideRules.canOverrideForConsumer(override.getName())) {
                invalidOverrides.add(override.getName());
            }
        }
        if (!invalidOverrides.isEmpty()) {
            String error = i18n.tr("Not allowed to override values for: {0}",
                StringUtils.join(invalidOverrides, ", "));
            throw new BadRequestException(error);
        }
    }

    public void validate(ContentOverride override) {
        List<ContentOverride> tmpList = new LinkedList<ContentOverride>();
        tmpList.add(override);
        validate(tmpList);
    }
}
