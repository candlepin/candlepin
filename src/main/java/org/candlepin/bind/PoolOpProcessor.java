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
package org.candlepin.bind;

import org.candlepin.audit.EventSink;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;



/**
 * Processes delayed pool operations stored in {@link PoolOperations}.
 */
public class PoolOpProcessor {

    private static final Logger log = LoggerFactory.getLogger(PoolOpProcessor.class);
    private final PoolCurator poolCurator;
    private final EventSink sink;

    @Inject
    public PoolOpProcessor(PoolCurator poolCurator, EventSink sink) {
        this.poolCurator = Objects.requireNonNull(poolCurator);
        this.sink = Objects.requireNonNull(sink);
    }

    public void process(PoolOperations operations) {
        this.createPools(operations.creations());
        this.setPoolQuantity(operations.updates());
    }

    private void createPools(List<Pool> pools) {
        if (CollectionUtils.isEmpty(pools)) {
            return;
        }

        Set<String> updatedPoolIds = new HashSet<>();

        for (Pool pool : pools) {
            // We're assuming that net-new pools will not yet have an ID here.
            if (pool.getId() != null) {
                updatedPoolIds.add(pool.getId());
            }
        }

        poolCurator.saveOrUpdateAll(pools, false, false);

        for (Pool pool : pools) {
            if (pool != null && !updatedPoolIds.contains(pool.getId())) {
                log.debug("  created pool: {}", pool);
                sink.emitPoolCreated(pool);
            }
            else {
                log.debug("  updated pool: {}", pool);
            }
        }
    }

    /**
     * Set the count of pools. The caller sets the absolute quantity.
     *   Current use is setting unlimited bonus pool to -1 or 0.
     */
    private void setPoolQuantity(Map<Pool, Long> poolQuantities) {
        if (MapUtils.isEmpty(poolQuantities)) {
            return;
        }

        poolCurator.lock(poolQuantities.keySet().stream()
            .filter(x -> !x.isLocked())
            .collect(Collectors.toSet()));
        for (Map.Entry<Pool, Long> entry : poolQuantities.entrySet()) {
            entry.getKey().setQuantity(entry.getValue());
        }
        poolCurator.mergeAll(poolQuantities.keySet(), false);
    }

}
