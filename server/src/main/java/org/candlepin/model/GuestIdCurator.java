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

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


/**
 * GuestIdCurator
 */
@Component
public class GuestIdCurator extends AbstractHibernateCurator<GuestId> {

    private static Logger log = LoggerFactory.getLogger(GuestIdCurator.class);

    public GuestIdCurator() {
        super(GuestId.class);
    }

    public CandlepinQuery<GuestId> listByConsumer(Consumer consumer) {
        DetachedCriteria criteria = this.createSecureDetachedCriteria()
            .add(Restrictions.eq("consumer", consumer));

        return this.cpQueryFactory.<GuestId>buildQuery(this.currentSession(), criteria);
    }

    public GuestId findByConsumerAndId(Consumer consumer, String guestId) {
        return (GuestId) this.currentSession().createCriteria(GuestId.class)
            .add(Restrictions.eq("consumer", consumer))
            .add(Restrictions.eq("guestIdLower", guestId.toLowerCase()))
            .setMaxResults(1)
            .uniqueResult();
    }

    public GuestId findByGuestIdAndOrg(String guestUuid, String ownerId) {
        return (GuestId) this.currentSession().createCriteria(GuestId.class)
            .add(Restrictions.eq("guestIdLower", guestUuid.toLowerCase()))
            .createAlias("consumer", "gconsumer")
            .add(Restrictions.eq("gconsumer.ownerId", ownerId))
            .setMaxResults(1)
            .uniqueResult();
    }
}
