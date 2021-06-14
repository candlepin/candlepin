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

import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;

import com.google.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.xnap.commons.i18n.I18n;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;



/**
 * ServiceLevelValidator utility class used to validate service levels.
 */
public class ServiceLevelValidator {

    private static final int MAX_COL_LENGTH = 255;
    private I18n i18n;
    private PoolManager poolManager;
    private OwnerCurator ownerCurator;

    @Inject
    public ServiceLevelValidator(I18n i18n, PoolManager poolManager, OwnerCurator ownerCurator) {
        this.i18n = i18n;
        this.poolManager = poolManager;
        this.ownerCurator = ownerCurator;
    }

    public void validate(String ownerId, Collection<String> serviceLevels) {
        Set<String> invalidServiceLevels = new HashSet<>();

        for (String serviceLevel : serviceLevels) {
            if (!StringUtils.isBlank(serviceLevel)) {
                boolean found = false;
                for (String level : poolManager.retrieveServiceLevelsForOwner(ownerId, false)) {
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
            Owner owner = ownerCurator.findOwnerById(ownerId);
            String error = i18n.tr("Service level \"{0}\" is not available to units of organization {1}.",
                StringUtils.join(invalidServiceLevels, ", "), owner.getKey());

            throw new BadRequestException(error);
        }
    }

    public void validate(String ownerId, String... serviceLevels) {
        this.validate(ownerId, Arrays.asList(serviceLevels));
    }
}
