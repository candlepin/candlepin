/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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
package org.candlepin.controller.refresher.visitors;

import org.candlepin.controller.refresher.mappers.NodeMapper;
import org.candlepin.controller.refresher.nodes.EntityNode;
import org.candlepin.controller.refresher.nodes.EntityNode.NodeState;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.service.model.SubscriptionInfo;



/**
 * A NodeVisitor implementation that supports pool entity nodes
 */
public class PoolNodeVisitor implements NodeVisitor<Pool, SubscriptionInfo> {

    /**
     * Creates a new PoolNodeVisitor instance
     */
    public PoolNodeVisitor(PoolCurator poolCurator) {
        // Intentionally left empty
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<Pool> getEntityClass() {
        return Pool.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processNode(NodeProcessor processor, NodeMapper mapper,
        EntityNode<Pool, SubscriptionInfo> node) {

        // Impl note:
        // We're doing nothing here at the moment since merging pools has a ton of code scattered
        // throughout the codebase (CandlepinPoolManager, PoolRules) and would require a lot of
        // attention.
        // Eventually, it should be consolidated, but for now we'll just do nothing and let the
        // existing code handle pools entirely.

        node.setNodeState(NodeState.SKIPPED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pruneNode(EntityNode<Pool, SubscriptionInfo> node) {
        // Intentionally left empty; see above for details
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void complete() {
        // Intentionally left empty
    }

}

