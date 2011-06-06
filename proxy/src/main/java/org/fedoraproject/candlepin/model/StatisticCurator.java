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


import org.fedoraproject.candlepin.model.Statistic.EntryType;

import com.wideplay.warp.persist.Transactional;

import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;

import java.util.Date;
import java.util.List;

/**
 * ContentCurator
 */
public class StatisticCurator extends AbstractHibernateCurator<Statistic> {

    public StatisticCurator() {
        super(Statistic.class);
    }

    @Transactional
    public Statistic create(Statistic s) {
        return super.create(s);
    }

    @SuppressWarnings("unchecked")
    public List<Statistic> getStatisticsByOwner(Owner owner, String qType, 
                                    String reference, Date from, Date to) {
        Criteria c = currentSession().createCriteria(Statistic.class);
        c.add(Restrictions.eq("ownerId", owner.getId()));
        if (qType != null && !qType.trim().equals("")) {
            if (qType.equals("TOTALCONSUMERS")) {
                c.add(Restrictions.eq("entryType", EntryType.TOTALCONSUMERS));
            }
            if (qType.equals("CONSUMERSBYSOCKETCOUNT")) {
                c.add(Restrictions.eq("entryType", EntryType.CONSUMERSBYSOCKETCOUNT));
            }
            if (qType.equals("TOTALSUBSCRIPTIONCOUNT")) {
                c.add(Restrictions.eq("entryType", EntryType.TOTALSUBSCRIPTIONCOUNT));
            }
            if (qType.equals("TOTALSUBSCRIPTIONCONSUMED")) {
                c.add(Restrictions.eq("entryType", EntryType.TOTALSUBSCRIPTIONCONSUMED));
            }
            if (qType.equals("PERPRODUCT")) {
                c.add(Restrictions.eq("entryType", EntryType.PERPRODUCT));
            }
            if (qType.equals("PERPOOL")) {
                c.add(Restrictions.eq("entryType", EntryType.PERPOOL));
            }
        }
        if (reference != null && !reference.trim().equals("")) {
            c.add(Restrictions.eq("valueReference", reference));
        }
        if (from != null) {
            c.add(Restrictions.ge("created", from));
        }
        if (to != null) {
            c.add(Restrictions.le("created", to));
        }
        return (List<Statistic>) c.list();

    }
}
