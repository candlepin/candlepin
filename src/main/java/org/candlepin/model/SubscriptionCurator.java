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
package org.candlepin.model;

import org.hibernate.Criteria;
import org.hibernate.criterion.CriteriaSpecification;
import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * Subscription manager.
 */
public class SubscriptionCurator extends AbstractHibernateCurator<Subscription> {

    private static Logger log = LoggerFactory.getLogger(SubscriptionCurator.class);

    protected SubscriptionCurator() {
        super(Subscription.class);
    }

    /**
     * Return Subscription for the given subscription id.
     * @param subId subscription id
     * @return subscription whose id matches the given value.
     */
    public Subscription lookupByOwnerAndId(String subId) {
        return (Subscription) currentSession().createCriteria(Subscription.class)
            .add(Restrictions.eq("id", subId)).uniqueResult();
    }

    /**
     * Return a list of subscriptions filtered by owner.
     * @param o Owner of the subscription.
     * @return a list of subscriptions filtered by owner.
     */
    @SuppressWarnings("unchecked")
    public List<Subscription> listByOwner(Owner o) {
        List<Subscription> subs = currentSession()
            .createCriteria(Subscription.class)
            .add(Restrictions.eq("owner", o))
                .createCriteria("product")
                    .add(Restrictions.ne("name", Product.ueberProductNameForOwner(o)))
            .list();
        if (subs == null) {
            return new LinkedList<Subscription>();
        }
        log.debug("Found subs: " + subs.size());
        return subs;
    }

    /**
     * Return an ueber subscription filtered by owner.
     * @param o Owner of the subscription.
     * @return an ueber subscription filtered by owner or null.
     */
    public Subscription findUeberSubscription(Owner o) {
        return (Subscription) currentSession()
            .createCriteria(Subscription.class)
            .add(Restrictions.eq("owner", o))
            .createCriteria("product")
            .add(Restrictions.eq("name", Product.ueberProductNameForOwner(o)))
            .uniqueResult();
    }

    /**
     * Return a list of subscriptions for the given product.
     *
     * We do essentially 2 queries here, so there is room for optimization
     * both in speed and memory usage.
     *
     * @param product product to search for.
     * @return a list of subscriptions
     */
    @SuppressWarnings("unchecked")
    public List<Subscription> listByProduct(Product product) {

        Criteria subscriptionCriteria = currentSession()
                .createCriteria(Subscription.class)
                .createAlias("providedProducts", "providedProduct",
                    CriteriaSpecification.LEFT_JOIN)
                .add(Restrictions.or(
                    Restrictions.eq("product", product),
                    Restrictions.eq("providedProduct.id", product.getId())
            ));

        List<Subscription> subs = subscriptionCriteria.list();
        if (subs == null) {
            return new LinkedList<Subscription>();
        }
        return subs;
    }

    /**
     * Return a list of subscriptions filtered by owner, product, since date.
     * @param o Owner of the subscription.
     * @param sinceDate date since modified.
     * @return a list of subscriptions filtered by owner, product, since date.
     */
    @SuppressWarnings("unchecked")
    public List<Subscription> listByOwnerSince(Owner o, Date sinceDate) {
        List<Subscription> subs = currentSession().createCriteria(
                Subscription.class)
            .add(Restrictions.eq("owner", o))
            .add(Restrictions.gt("modified", sinceDate)).list();
        if (subs == null) {
            return new LinkedList<Subscription>();
        }
        return subs;
    }


    /**
     * Return list of subscriptions from added since the given date.
     * @param sinceDate Date used in searches.
     * @return list of subscriptions from added since the given date.
     */
    @SuppressWarnings("unchecked")
    public List<Subscription> listSince(Date sinceDate) {
        List<Subscription> subs = currentSession().createCriteria(
                Subscription.class)
            .add(Restrictions.gt("modified", sinceDate)).list();
        if (subs == null) {
            return new LinkedList<Subscription>();
        }
        return subs;
    }
}
