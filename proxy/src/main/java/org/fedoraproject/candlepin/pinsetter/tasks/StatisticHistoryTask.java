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
package org.fedoraproject.candlepin.pinsetter.tasks;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.Statistic;
import org.fedoraproject.candlepin.model.Statistic.EntryType;
import org.fedoraproject.candlepin.model.Statistic.ValueType;
import org.fedoraproject.candlepin.model.StatisticCurator;

import com.google.inject.Inject;

import org.apache.log4j.Logger;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;

/**
 * StatisticHistoryTask.
 */
public class StatisticHistoryTask implements Job {

    public static final String DEFAULT_SCHEDULE = "0 0 1 * * ?"; // run every
                                                                 // day at 1 AM

    private EntityManager entityManager;
    private StatisticCurator statCurator;
    private OwnerCurator ownerCurator;
    private ConsumerCurator consumerCurator;

    private static Logger log = Logger.getLogger(StatisticHistoryTask.class);

    /**
     * Instantiates a new certificate revocation list task.
     *
     * @param entityManager the EntityManager
     * @param statCurator the StatisticCurator
     */
    @Inject
    public StatisticHistoryTask(EntityManager entityManager,
        StatisticCurator statCurator, OwnerCurator ownerCurator,
        ConsumerCurator consumerCurator) {
        this.entityManager = entityManager;
        this.statCurator = statCurator;
        this.ownerCurator = ownerCurator;
        this.consumerCurator = consumerCurator;
    }

    @Override
    public void execute(JobExecutionContext ctx) throws JobExecutionException {
        log.info("Executing Statistic History Job.");

        try {
            List<Owner> owners = ownerCurator.listAll();
            for (Owner owner : owners) {
                String ownerId = owner.getId();

                totalConsumers(owner);
                consumersPerSocketCount(owner);
                int tsc = totalSubscriptionCount(ownerId);
                totalSubscriptionConsumed(ownerId, tsc);
                perPool(ownerId);
                perProduct(ownerId);
            }

        }
        catch (HibernateException e) {
            log.error("Cannot store: ", e);
        }
    }

    private void totalConsumers(Owner owner) {
        int count = owner.getConsumers().size();
        Statistic consumerCountStatistic = new Statistic(
            EntryType.TotalConsumers, ValueType.Raw, null, count, owner.getId());
        statCurator.create(consumerCountStatistic);
    }

    private void consumersPerSocketCount(Owner owner) {
        Map<String, Integer> map = new HashMap<String, Integer>();
        for (Consumer c : owner.getConsumers()) {
            String sockets = c.getFact("cpu.cpu_socket(s)");
            Integer count = map.get(sockets);
            if (count == null) {
                count = new Integer(0);
            }
            count++;
            map.put(sockets, count);
        }
        for (String sockets : map.keySet()) {
            Statistic socketCountStatistic = new Statistic(
                EntryType.ConsumersBySocketCount, ValueType.Raw, sockets,
                map.get(sockets), owner.getId());
            statCurator.create(socketCountStatistic);
        }
    }

    private int totalSubscriptionCount(String ownerId) {
        int subscriptionCountTotal = 0;
        String subscriptionCountString = "select sum(quantity) from Pool p" +
            " where p.owner.id = :ownerId";
        Query subscriptionCountQuery = currentSession().createQuery(
            subscriptionCountString).setString("ownerId", ownerId);
        Long subCount = (Long) subscriptionCountQuery.iterate().next();
        subscriptionCountTotal = (subCount == null ? 0 : subCount.intValue());
        Statistic subscriptionCountStatistic = new Statistic(
            EntryType.TotalSubscriptionCount, ValueType.Raw, null,
            subscriptionCountTotal, ownerId);
        statCurator.create(subscriptionCountStatistic);
        return subscriptionCountTotal;
    }

    private void totalSubscriptionConsumed(String ownerId,
        int subscriptionCountTotal) {

        // Total Subscription Consumed (Raw Count and Percentage)
        int entitlementCountTotal = 0;
        String entitlementCountString = "select sum(quantity) from Entitlement e" +
            " where e.owner.id = :ownerId";
        Query entitlementCountQuery = currentSession().createQuery(
            entitlementCountString).setString("ownerId", ownerId);
        Long entCount = (Long) entitlementCountQuery.iterate().next();
        entitlementCountTotal = (entCount == null ? 0 : entCount.intValue());
        Statistic entitlementCountRawStatistic = new Statistic(
            EntryType.TotalSubscriptionConsumed, ValueType.Raw, null,
            entitlementCountTotal, ownerId);
        statCurator.create(entitlementCountRawStatistic);
        int percentage = 0;
        if (subscriptionCountTotal > 0) {
            percentage = (entitlementCountTotal * 100 / subscriptionCountTotal);
        }
        Statistic entitlementCountPercentageStatistic = new Statistic(
            EntryType.TotalSubscriptionConsumed, ValueType.Percentage, null,
            percentage, ownerId);
        statCurator.create(entitlementCountPercentageStatistic);
    }

    private void perPool(String ownerId) {
        String poolString = "select id from Pool p" +
            " where p.owner.id = :ownerId";
        Query poolQuery = currentSession().createQuery(poolString).setString(
            "ownerId", ownerId);
        Iterator poolIter = poolQuery.iterate();
        String perPoolString = "select count(e) from Event e " +
            "where e.target = 'ENTITLEMENT' and e.referenceType = 'POOL' " +
            "and e.type = :type and e.referenceId = :referenceId";
        while (poolIter.hasNext()) {
            String poolId = (String) poolIter.next();

            Query perPoolQuery = currentSession().createQuery(perPoolString);
            perPoolQuery.setString("type", "CREATED");
            perPoolQuery.setString("referenceId", poolId);
            Long ppUCount = (Long) perPoolQuery.iterate().next();
            int perPoolUsedCount = (ppUCount == null ? 0 : ppUCount.intValue());

            perPoolQuery.setString("type", "DELETED");
            Long ppDCount = (Long) perPoolQuery.iterate().next();
            int perPoolDeletedCount = (ppDCount == null ? 0 : ppDCount
                .intValue());
            int perPoolConsumedCount = perPoolUsedCount - perPoolDeletedCount;

            String totalPoolCountString = "select quantity from Pool p" +
                " where p.id = :poolId";
            Query totalPoolCountQuery = currentSession().createQuery(
                totalPoolCountString).setString("poolId", poolId);

            Long tpCount = (Long) totalPoolCountQuery.iterate().next();
            int totalPoolCountTotal = (tpCount == null ? 0 : tpCount.intValue());
            int poolPercentage = 0;
            if (totalPoolCountTotal > 0) {
                poolPercentage = (perPoolConsumedCount * 100 / totalPoolCountTotal);
            }
            Statistic perPoolCountPercentageStatistic = new Statistic(
                EntryType.PerPool, ValueType.Percentage, poolId,
                poolPercentage, ownerId);
            statCurator.create(perPoolCountPercentageStatistic);
            Statistic perPoolCountUsedStatistic = new Statistic(
                EntryType.PerPool, ValueType.Used, poolId, perPoolUsedCount,
                ownerId);
            statCurator.create(perPoolCountUsedStatistic);
            Statistic perPoolCountConsumedStatistic = new Statistic(
                EntryType.PerPool, ValueType.Consumed, poolId,
                perPoolConsumedCount, ownerId);
            statCurator.create(perPoolCountConsumedStatistic);
        }
    }

    private void perProduct(String ownerId) {
        String productString = "select distinct p.productName from Pool p" +
            " where p.owner.id = :ownerId";
        Query productQuery = currentSession().createQuery(productString)
            .setString("ownerId", ownerId);
        Iterator productIter = productQuery.iterate();
        String perProductString = "select count(e) from Event e " +
            "where e.target = 'ENTITLEMENT' and e.referenceType = 'POOL' " +
            "and e.type = :type and e.targetName = :productName";
        while (productIter.hasNext()) {
            String productName = (String) productIter.next();

            Query perProductQuery = currentSession().createQuery(
                perProductString);
            perProductQuery.setString("type", "CREATED");
            perProductQuery.setString("productName", productName);
            Long ppUCount = (Long) perProductQuery.iterate().next();
            int perProductUsedCount = (ppUCount == null ? 0 : ppUCount
                .intValue());

            perProductQuery.setString("type", "DELETED");
            Long ppDCount = (Long) perProductQuery.iterate().next();
            int perProductDeletedCount = (ppDCount == null ? 0 : ppDCount
                .intValue());
            int perProductConsumedCount = perProductUsedCount -
                perProductDeletedCount;

            String totalProductCountString = "select sum(quantity) from Pool p" +
                " where p.productName = :productName";
            Query totalProductCountQuery = currentSession().createQuery(
                totalProductCountString).setString("productName", productName);
            Long tpCount = (Long) totalProductCountQuery.iterate().next();
            int totalProductCountTotal = (tpCount == null ? 0 : tpCount
                .intValue());
            int productPercentage = 0;
            if (totalProductCountTotal > 0) {
                productPercentage = (perProductConsumedCount * 100 /
                                     totalProductCountTotal);
            }
            Statistic perPoolCountPercentageStatistic = new Statistic(
                EntryType.PerProduct, ValueType.Percentage, productName,
                productPercentage, ownerId);
            statCurator.create(perPoolCountPercentageStatistic);
            Statistic perPoolCountUsedStatistic = new Statistic(
                EntryType.PerProduct, ValueType.Used, productName,
                perProductUsedCount, ownerId);
            statCurator.create(perPoolCountUsedStatistic);
            Statistic perPoolCountConsumedStatistic = new Statistic(
                EntryType.PerProduct, ValueType.Consumed, productName,
                perProductConsumedCount, ownerId);
            statCurator.create(perPoolCountConsumedStatistic);
        }
    }

    protected Session currentSession() {
        Session sess = (Session) entityManager.getDelegate();
        return sess;
    }
}
