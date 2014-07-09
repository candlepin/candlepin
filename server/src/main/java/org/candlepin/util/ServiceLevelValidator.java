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
import org.candlepin.controller.PoolManager;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.Owner;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;

/**
 * ContentOverrideValidator utility class used to validate
 * ContentOverride and its subclasses.  Includes column length
 * checking and overrideRules to check against blacklisted
 * name overrides
 */
public class ServiceLevelValidator {

    private static final int MAX_COL_LENGTH = 255;
    private I18n i18n;
    private PoolManager poolManager;

    @Inject
    public ServiceLevelValidator(I18n i18n, PoolManager poolManager) {
        this.i18n = i18n;
        this.poolManager = poolManager;
    }

    public void validate(Owner owner, Collection<String> serviceLevels) {
        Set<String> invalidServiceLevels = new HashSet<String>();
        for (String serviceLevel : serviceLevels) {
            if (!StringUtils.isBlank(serviceLevel)) {
                boolean found = false;
                for (String level : poolManager.retrieveServiceLevelsForOwner(
                    owner, false)) {
                    if (serviceLevel.equalsIgnoreCase(level)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    invalidServiceLevels.add(serviceLevel);
                }
            }
        }
        if (!invalidServiceLevels.isEmpty()) {
            String error = i18n.tr("Service level ''{0}'' is not available " +
                "to units of organization {1}.",
                StringUtils.join(invalidServiceLevels, ", "), owner.getKey());
            throw new BadRequestException(error);
        }
    }

    public void validate(Owner owner, String serviceLevel) {
        List<String> tmpList = new LinkedList<String>();
        tmpList.add(serviceLevel);
        validate(owner, tmpList);
    }
}
