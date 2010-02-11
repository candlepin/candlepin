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

import java.util.LinkedList;
import java.util.List;

public class SubscriptionCurator extends AbstractHibernateCurator<Subscription> {

    protected SubscriptionCurator() {
        super(Subscription.class);
    }
    
    public List<Subscription> listByOwnerAndProduct(Owner o, String productId) {
        List<Subscription> subs = (List<Subscription>)currentSession().createCriteria(Subscription.class)
            .add(Restrictions.eq("owner", o))
            .add(Restrictions.eq("productId", productId)).list();
        if (subs == null) {
            return new LinkedList<Subscription>();
        }
        return subs;
    }
    
    public Subscription lookupByOwnerAndId(Owner o, Long subId) {
        return (Subscription)currentSession().createCriteria(Subscription.class)
            .add(Restrictions.eq("owner", o))
            .add(Restrictions.eq("id", subId)).uniqueResult();
    }

}
