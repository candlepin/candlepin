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

import com.google.inject.Inject;

import org.candlepin.model.Statistic.EntryType;
import org.candlepin.model.Statistic.ValueType;
import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * StatisticCuratorQueries
 */
public class StatisticCuratorQueries extends AbstractHibernateCurator<Statistic>  {

    @Inject
    public StatisticCuratorQueries() {
        super(Statistic.class);
    }

    private static final Map<String, EntryType> QTYPES = new HashMap<String, EntryType>() {
        {
            put("TOTALCONSUMERS", EntryType.TOTALCONSUMERS);
            put("CONSUMERSBYSOCKETCOUNT", EntryType.CONSUMERSBYSOCKETCOUNT);
            put("TOTALSUBSCRIPTIONCOUNT", EntryType.TOTALSUBSCRIPTIONCOUNT);
            put("TOTALSUBSCRIPTIONCONSUMED", EntryType.TOTALSUBSCRIPTIONCONSUMED);
            put("PERPRODUCT", EntryType.PERPRODUCT);
            put("PERPOOL", EntryType.PERPOOL);
            put("SYSTEM", EntryType.SYSTEM);
        }
    };

    private static final Map<String, ValueType> VTYPES = new HashMap<String, ValueType>() {
        {
            put("RAW", ValueType.RAW);
            put("USED", ValueType.USED);
            put("CONSUMED", ValueType.CONSUMED);
            put("PERCENTAGECONSUMED", ValueType.PERCENTAGECONSUMED);
            put("PHYSICAL", ValueType.PHYSICAL);
            put("VIRTUAL", ValueType.VIRTUAL);
        }
    };

    private static final Map<String, List<String>> VALID_VTYPES =
        new HashMap<String, List<String>>() {
            {
                put("byOwner", new ArrayList<String>() {
                    {
                        add("RAW");
                        add("USED");
                        add("CONSUMED");
                        add("PERCENTAGECONSUMED");
                        add("PHYSICAL");
                        add("VIRTUAL");
                    }
                });
                put("byProduct", new ArrayList<String>() {
                    {
                        add("RAW");
                        add("USED");
                        add("CONSUMED");
                        add("PERCENTAGECONSUMED");
                    }
                });
                put("byPool", new ArrayList<String>() {
                    {
                        add("RAW");
                        add("USED");
                        add("CONSUMED");
                        add("PERCENTAGECONSUMED");
                    }
                });
            }
        };

    public List<Statistic> getStatisticsByOwner(Owner owner, String qType,
        String reference, String vType, Date from, Date to) {

        return getStatisticsBy(owner, qType, reference, vType, from,
            to, VALID_VTYPES.get("byOwner"));
    }

    public List<Statistic> getStatisticsByPool(String poolId, String vType,
        Date from, Date to) {

        return getStatisticsBy(null, "PERPOOL", poolId, vType, from,
            to, VALID_VTYPES.get("byProduct"));
    }

    public List<Statistic> getStatisticsByProduct(String prodId, String vType,
        Date from, Date to) {

        return getStatisticsBy(null, "PERPRODUCT", prodId, vType, from,
            to, VALID_VTYPES.get("byPool"));

    }

    @SuppressWarnings("unchecked")
    private List<Statistic> getStatisticsBy(Owner owner, String qType,
        String reference, String vType, Date from, Date to,
        List<String> validVTypes) {

        Criteria c = currentSession().createCriteria(Statistic.class);

        if (owner != null) {
            c.add(Restrictions.eq("ownerId", owner.getId()));
        }

        generateEntryTypeFilter(c, qType);

        if (validVTypes.contains(vType)) {
            generateValueTypeFilter(c, vType);
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

    private void generateEntryTypeFilter(Criteria c, String qType) {
        if (qType != null && !qType.trim().equals("")) {
            EntryType type = QTYPES.get(qType);
            if (type != null) {
                c.add(Restrictions.eq("entryType", type));
            }
        }
    }

    private void generateValueTypeFilter(Criteria c, String vType) {
        if (vType != null && !vType.trim().equals("")) {
            ValueType type = VTYPES.get(vType);
            c.add(Restrictions.eq("valueType", type));
        }
    }
}
