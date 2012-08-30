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
package org.candlepin.model.curator;

import java.util.Date;
import java.util.List;

import org.candlepin.model.Owner;
import org.candlepin.model.Statistic;
import org.candlepin.model.Statistic.EntryType;
import org.candlepin.model.Statistic.ValueType;
import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;

import com.google.inject.Inject;

/**
 * StatisticCuratorQueries
 */
public class StatisticCuratorQueries extends AbstractHibernateCurator<Statistic>  {

    @Inject
    public StatisticCuratorQueries() {
        super(Statistic.class);
    }

    @SuppressWarnings("unchecked")
    public List<Statistic> getStatisticsByOwner(Owner owner, String qType,
        String reference, String vType, Date from, Date to) {
        Criteria c = currentSession().createCriteria(Statistic.class);
        c.add(Restrictions.eq("ownerId", owner.getId()));
        if (qType != null && !qType.trim().equals("")) {
            if (qType.equals("TOTALCONSUMERS")) {
                c.add(Restrictions.eq("entryType", EntryType.TOTALCONSUMERS));
            }
            else if (qType.equals("CONSUMERSBYSOCKETCOUNT")) {
                c.add(Restrictions.eq("entryType",
                    EntryType.CONSUMERSBYSOCKETCOUNT));
            }
            else if (qType.equals("TOTALSUBSCRIPTIONCOUNT")) {
                c.add(Restrictions.eq("entryType",
                    EntryType.TOTALSUBSCRIPTIONCOUNT));
            }
            else if (qType.equals("TOTALSUBSCRIPTIONCONSUMED")) {
                c.add(Restrictions.eq("entryType",
                    EntryType.TOTALSUBSCRIPTIONCONSUMED));
            }
            else if (qType.equals("PERPRODUCT")) {
                c.add(Restrictions.eq("entryType", EntryType.PERPRODUCT));
            }
            else if (qType.equals("PERPOOL")) {
                c.add(Restrictions.eq("entryType", EntryType.PERPOOL));
            }
            else if (qType.equals("SYSTEM")) {
                c.add(Restrictions.eq("entryType", EntryType.SYSTEM));
            }
            else {
                // no match, no filter
            }
        }
        if (vType != null && !vType.trim().equals("")) {
            if (vType.equals("RAW")) {
                c.add(Restrictions.eq("valueType", ValueType.RAW));
            }
            else if (vType.equals("USED")) {
                c.add(Restrictions.eq("valueType", ValueType.USED));
            }
            else if (vType.equals("CONSUMED")) {
                c.add(Restrictions.eq("valueType", ValueType.CONSUMED));
            }
            else if (vType.equals("PERCENTAGECONSUMED")) {
                c.add(Restrictions
                    .eq("valueType", ValueType.PERCENTAGECONSUMED));
            }
            else if (vType.equals("PHYSICAL")) {
                c.add(Restrictions.eq("valueType", ValueType.PHYSICAL));
            }
            else if (vType.equals("VIRTUAL")) {
                c.add(Restrictions.eq("valueType", ValueType.VIRTUAL));
            }
            else {
                // no match, no filter
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

    @SuppressWarnings("unchecked")
    public List<Statistic> getStatisticsByPool(String poolId, String vType,
        Date from, Date to) {
        Criteria c = currentSession().createCriteria(Statistic.class);
        c.add(Restrictions.eq("valueReference", poolId));
        c.add(Restrictions.eq("entryType", EntryType.PERPOOL));

        if (vType != null && !vType.trim().equals("")) {
            if (vType.equals("RAW")) {
                c.add(Restrictions.eq("valueType", ValueType.RAW));
            }
            else if (vType.equals("USED")) {
                c.add(Restrictions.eq("valueType", ValueType.USED));
            }
            else if (vType.equals("CONSUMED")) {
                c.add(Restrictions.eq("valueType", ValueType.CONSUMED));
            }
            else if (vType.equals("PERCENTAGECONSUMED")) {
                c.add(Restrictions
                    .eq("valueType", ValueType.PERCENTAGECONSUMED));
            }
            else {
                // no match, no filter
            }
        }
        if (from != null) {
            c.add(Restrictions.ge("created", from));
        }
        if (to != null) {
            c.add(Restrictions.le("created", to));
        }
        return (List<Statistic>) c.list();

    }

    @SuppressWarnings("unchecked")
    public List<Statistic> getStatisticsByProduct(String prodId, String vType,
        Date from, Date to) {
        Criteria c = currentSession().createCriteria(Statistic.class);
        c.add(Restrictions.eq("entryType", EntryType.PERPRODUCT));

        c.add(Restrictions.eq("valueReference", prodId));
        if (vType != null && !vType.trim().equals("")) {
            if (vType.equals("RAW")) {
                c.add(Restrictions.eq("valueType", ValueType.RAW));
            }
            else if (vType.equals("USED")) {
                c.add(Restrictions.eq("valueType", ValueType.USED));
            }
            else if (vType.equals("CONSUMED")) {
                c.add(Restrictions.eq("valueType", ValueType.CONSUMED));
            }
            else if (vType.equals("PERCENTAGECONSUMED")) {
                c.add(Restrictions
                    .eq("valueType", ValueType.PERCENTAGECONSUMED));
            }
            else {
                // no match, no filter
            }
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
