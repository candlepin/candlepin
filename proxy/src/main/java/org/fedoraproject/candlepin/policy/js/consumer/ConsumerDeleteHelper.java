/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.policy.js.consumer;

import java.util.List;

import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.PoolCurator;
import org.fedoraproject.candlepin.policy.ValidationResult;

import com.google.inject.Inject;
import org.fedoraproject.candlepin.controller.PoolManager;

/**
 * PostRevocationHelper
 */
public class ConsumerDeleteHelper {

    private ValidationResult result = new ValidationResult();
    private PoolCurator poolCurator;
    private PoolManager poolManager;

    @Inject
    public ConsumerDeleteHelper(PoolManager poolManager, PoolCurator poolCurator) {
        this.poolManager = poolManager;
        this.poolCurator = poolCurator;
    }

    public void deleteUserRestrictedPools(String username) {
        List<Pool> userRestrictedPools
            = poolCurator.listPoolsRestrictedToUser(username);

        for (Pool pool : userRestrictedPools) {
            poolManager.deletePool(pool);
        }
    }

    /**
     * Add an error message to the validation results.
     * @param resourceKey key of the error message.
     */
    public void addError(String resourceKey) {
        result.addError(resourceKey);
    }

    /**
     * Add a warning message to the validation.
     * @param resourceKey key
     */
    public void addWarning(String resourceKey) {
        result.addWarning(resourceKey);
    }

    /**
     * Return the result of the validation
     * @return the result of the validation
     */
    public ValidationResult getResult() {
        return result;
    }
}
