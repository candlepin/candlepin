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
package org.candlepin.policy.criteria;

import org.candlepin.config.Configuration;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolFilterBuilder;

import com.google.inject.Inject;

import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Subqueries;

import java.util.LinkedList;
import java.util.List;



/**
 * CriteriaRules
 *
 * A class used to generate database criteria for filtering out rules that are not applicable for a
 * consumer before running them through a rules check.
 */
public class CriteriaRules  {

    protected Configuration config;
    protected ConsumerCurator consumerCurator;
    protected ConsumerTypeCurator consumerTypeCurator;

    @Inject
    public CriteriaRules(Configuration config, ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator) {

        this.config = config;
        this.consumerCurator = consumerCurator;
        this.consumerTypeCurator = consumerTypeCurator;
    }

    /**
     * Create a List of JPA criterion that can filter out pools that are not
     * applicable to consumer. Helps to scale down large numbers of pools
     * specifically with virt_limit subscriptions.
     *
     * @param consumer The consumer we are filtering pools for
     * @return List of Criterion
     */
    @SuppressWarnings("checkstyle:indentation")
    public List<Criterion> availableEntitlementCriteria(Consumer consumer) {

        // avoid passing in a consumerCurator just to get the host
        // consumer UUID
        Consumer hostConsumer = null;
        if (consumer.getFact("virt.uuid") != null) {
            hostConsumer = consumerCurator.getHost(consumer.getFact("virt.uuid"), consumer.getOwnerId());
        }

        List<Criterion> criteriaFilters = new LinkedList<>();
        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);

        // Don't load virt_only pools if this consumer isn't a guest
        // or a manifest consumer
        if (ctype.isManifest()) {
            DetachedCriteria requiresHost = DetachedCriteria.forClass(Pool.class, "pool2")
                .createAlias("pool2.attributes", "attrib")
                .add(Restrictions.eqProperty("pool2.id", "id"))
                .add(Restrictions.eq("attrib.indicies", Pool.Attributes.REQUIRES_HOST))
                .setProjection(Projections.id());

            // we do want everything else
            criteriaFilters.add(Subqueries.notExists(requiresHost));
        }
        else if (!consumer.isGuest()) {
            PoolFilterBuilder filterBuilder = new PoolFilterBuilder();
            filterBuilder.addAttributeFilter("virt_only", "true");
            criteriaFilters.add(Restrictions.not(filterBuilder.getCriteria()));
        }
        else {
            // we are a virt guest
            // add criteria for filtering out pools that are not for this guest
            if (consumer.hasFact("virt.uuid")) {
                String hostUuid = ""; // need a default value in case there is no host
                if (hostConsumer != null) {
                    hostUuid = hostConsumer.getUuid();
                }

                // Note: looking for pools that are not for this guest
                // we do want everything else
                DetachedCriteria wrongRequiresHost = DetachedCriteria.forClass(Pool.class, "pool2")
                    .createAlias("pool2.attributes", "attrib")
                    .add(Restrictions.eqProperty("pool2.id", "id"))
                    .add(Restrictions.eq("attrib.indicies", Pool.Attributes.REQUIRES_HOST))
                    .add(Restrictions.ne("attrib.elements", hostUuid).ignoreCase())
                    .setProjection(Projections.id());

                criteriaFilters.add(Subqueries.notExists(wrongRequiresHost));
            }

            // no virt.uuid, we can't try to filter
        }

        return criteriaFilters;
    }



}
