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

import java.util.LinkedList;
import java.util.List;

import org.candlepin.config.Config;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.PoolAttribute;
import org.candlepin.model.ProductPoolAttribute;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Property;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Subqueries;

import com.google.inject.Inject;

/**
 * CriteriaRules
 *
 * A class used to generate database criteria for filtering
 * out rules that are not applicable for a consumer before
 * running them through a rules check.
 *
 */
public class CriteriaRules  {

    protected Config config;
    protected ConsumerCurator consumerCurator;

    @Inject
    public CriteriaRules(Config config,
            ConsumerCurator consumerCurator) {
        this.config = config;
        this.consumerCurator = consumerCurator;
    }

    /**
     * Create a List of JPA criterion that can filter out pools that are not
     * applicable to consumer. Helps to scale down large numbers of pools
     * specifically with virt_limit subscriptions.
     *
     * @param consumer The consumer we are filtering pools for
     * @return List of Criterion
     */
    public List<Criterion> availableEntitlementCriteria(Consumer consumer) {

        // avoid passing in a consumerCurator just to get the host
        // consumer UUID
        Consumer hostConsumer = null;
        if (consumer.getFact("virt.uuid") != null) {
            hostConsumer = consumerCurator.getHost(
                consumer.getFact("virt.uuid"), consumer.getOwner());
        }

        List<Criterion> criteriaFilters = new LinkedList<Criterion>();

        // Don't load virt_only pools if this consumer isn't a guest
        // or a manifest consumer
        if (consumer.getType().isManifest()) {
            DetachedCriteria noRequiresHost = DetachedCriteria.forClass(
                    PoolAttribute.class, "attr")
                .add(Restrictions.eq("name", "requires_host"))
                .add(Property.forName("this.id").eqProperty("attr.pool.id"))
                .setProjection(Projections.property("attr.id"));

            // we do want everything else
            criteriaFilters.add(Subqueries.notExists(noRequiresHost));
        }
        else if (!"true".equalsIgnoreCase(consumer.getFact("virt.is_guest"))) {
            // not a guest
            DetachedCriteria noVirtOnlyPoolAttr =
                DetachedCriteria.forClass(
                        PoolAttribute.class, "pool_attr")
                    .add(Restrictions.eq("name", "virt_only"))
                    .add(Restrictions.eq("value", "true").ignoreCase())
                    .add(Property.forName("this.id")
                            .eqProperty("pool_attr.pool.id"))
                    .setProjection(Projections.property("pool_attr.id"));
            criteriaFilters.add(Subqueries.notExists(
                    noVirtOnlyPoolAttr));

            // same criteria but for PoolProduct attributes
            // not sure if this should be two separate criteria, or if it's
            // worth it to combine in some clever fashion
            DetachedCriteria noVirtOnlyProductAttr =
                DetachedCriteria.forClass(
                        ProductPoolAttribute.class, "prod_attr")
                    .add(Restrictions.eq("name", "virt_only"))
                    .add(Restrictions.eq("value", "true").ignoreCase())
                    .add(Property.forName("this.id")
                            .eqProperty("prod_attr.pool.id"))
                    .setProjection(Projections.property("prod_attr.id"));
            criteriaFilters.add(Subqueries.notExists(
                    noVirtOnlyProductAttr));

        }
        else {
            // we are a virt guest
            // add criteria for filtering out pools that are not for this guest
            if (consumer.hasFact("virt.uuid")) {
                String hostUuid = ""; // need a default value in case there is no host
                if (hostConsumer != null) {
                    hostUuid = hostConsumer.getUuid();
                }
                DetachedCriteria noRequiresHost = DetachedCriteria.forClass(
                        PoolAttribute.class, "attr")
                        .add(Restrictions.eq("name", "requires_host"))
                        //  Note: looking for pools that are not for this guest
                        .add(Restrictions.ne("value", hostUuid).ignoreCase())
                        .add(Property.forName("this.id")
                                .eqProperty("attr.pool.id"))
                                .setProjection(Projections.property("attr.id"));
                // we do want everything else
                criteriaFilters.add(Subqueries.notExists(
                        noRequiresHost));
            }
            // no virt.uuid, we can't try to filter
        }

        return criteriaFilters;
    }



}
