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
package org.fedoraproject.candlepin.model;

import org.hibernate.criterion.Restrictions;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * Subscription manager.
 */
public class SubscriptionCurator extends AbstractHibernateCurator<Subscription> {

    protected SubscriptionCurator() {
        super(Subscription.class);
    }

    /**
     * Returns a list of subscriptions filtered by owner and product.
     * @param o Owner of the subscription.
     * @param productId Product Id to filter the subscription for.
     * @return a list of subscriptions filtered by owner and product.
     */
    public List<Subscription> listByOwnerAndProduct(Owner o, String productId) {
        List<Subscription> subs = (List<Subscription>) currentSession()
            .createCriteria(Subscription.class)
            .add(Restrictions.eq("owner", o))
            .add(Restrictions.eq("productId", productId)).list();

        if (subs == null) {
            return new LinkedList<Subscription>();
        }
        return subs;
    }

    /**
     * Return Subscription for the given subscription id.
     * @param subId subscription id
     * @return subscription whose id matches the given value.
     */
    public Subscription lookupByOwnerAndId(Long subId) {
        return (Subscription) currentSession().createCriteria(Subscription.class)
            .add(Restrictions.eq("id", subId)).uniqueResult();
    }

    /**
     * Return a list of subscriptions filtered by owner, product, since date.
     * @param o Owner of the subscription.
     * @param sinceDate date since modified.
     * @return a list of subscriptions filtered by owner, product, since date.
     */
    public List<Subscription> listByOwnerAndProductSince(Owner o, Date sinceDate) {
        List<Subscription> subs = (List<Subscription>) currentSession().createCriteria(
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
    public List<Subscription> listSince(Date sinceDate) {
        List<Subscription> subs = (List<Subscription>) currentSession().createCriteria(
                Subscription.class)
            .add(Restrictions.gt("modified", sinceDate)).list();
        if (subs == null) {
            return new LinkedList<Subscription>();
        }
        return subs;
    }

}
