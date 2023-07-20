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
package org.candlepin.policy.js.consumer;

import org.candlepin.controller.PoolService;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;

import java.util.List;

import javax.inject.Inject;



public class ConsumerRules {

    private final PoolCurator poolCurator;
    private final PoolService poolService;
    private final ConsumerTypeCurator consumerTypeCurator;

    @Inject
    public ConsumerRules(PoolService poolService, PoolCurator poolCurator,
        ConsumerTypeCurator consumerTypeCurator) {

        this.poolService = poolService;
        this.poolCurator = poolCurator;
        this.consumerTypeCurator = consumerTypeCurator;
    }

    public void onConsumerDelete(Consumer consumer) {
        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);

        // Cleanup user restricted pools:
        if (ctype.isType(ConsumerTypeEnum.PERSON)) {
            List<Pool> userRestrictedPools = poolCurator
                .listPoolsRestrictedToUser(consumer.getUsername());

            for (Pool pool : userRestrictedPools) {
                this.poolService.deletePool(pool);
            }
        }
    }
}
