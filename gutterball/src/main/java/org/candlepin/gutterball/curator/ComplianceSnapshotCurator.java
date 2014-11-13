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

package org.candlepin.gutterball.curator;

import org.candlepin.gutterball.model.snapshot.Compliance;

import com.google.inject.Inject;

import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Subqueries;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;



/**
 * The curator responsible for managing {@link Compliance} objects.
 *
 */
public class ComplianceSnapshotCurator extends BaseCurator<Compliance> {
    private ConsumerStateCurator consumerStateCurator;

    @Inject
    public ComplianceSnapshotCurator(ConsumerStateCurator consumerStateCurator) {
        super(Compliance.class);
        this.consumerStateCurator = consumerStateCurator;
    }

    public List<Compliance> getSnapshotsOnDate(
            Date targetDate, List<String> consumerUuids,
            List<String> ownerFilters, List<String> statusFilters) {

        List<String> activeConsumers =
                consumerStateCurator.getConsumerUuidsOnDate(targetDate, ownerFilters, consumerUuids);

        // https://hibernate.atlassian.net/browse/HHH-2776
        if (activeConsumers == null || activeConsumers.isEmpty()) {
            return new ArrayList<Compliance>();
        }

        DetachedCriteria mainQuery = DetachedCriteria.forClass(Compliance.class);
        mainQuery.createAlias("consumer", "c");
        mainQuery.add(Restrictions.in("c.uuid", activeConsumers));

        if (ownerFilters != null && !ownerFilters.isEmpty()) {
            mainQuery.createAlias("c.owner", "o");
            mainQuery.add(Restrictions.in("o.key", ownerFilters));
        }

        Date toCheck = targetDate == null ? new Date() : targetDate;
        mainQuery.add(Restrictions.le("date", toCheck));

        mainQuery.setProjection(
            Projections.projectionList()
                .add(Projections.max("date"))
                .add(Projections.groupProperty("c.uuid"))
        );

        // Post query filter on Status.
        Criteria postFilter = currentSession().createCriteria(Compliance.class)
            .createAlias("consumer", "cs")
            .add(Subqueries.propertiesIn(new String[] {"date", "cs.uuid"}, mainQuery));

        if (statusFilters != null && !statusFilters.isEmpty()) {
            postFilter.createAlias("status", "stat");
            postFilter.add(Restrictions.in("stat.status", statusFilters));
        }

        return postFilter.list();
    }

    public Set<Compliance> getComplianceForTimespan(Date startDate, Date endDate,
            List<String> consumerIds, List<String> owners) {

        // If the start date is null, we can return all status updates.
        // Otherwise, we need to get every consumers latest compliance info at that point.
        Set<Compliance> snaps = new HashSet<Compliance>();
        if (startDate != null) {
            // Don't restrict by status here, it may not match to begin with, we only care if it matches
            snaps.addAll(getSnapshotsOnDate(startDate, consumerIds, owners, null));
        }

        Criteria mainQuery = currentSession().createCriteria(Compliance.class);
        mainQuery.createAlias("consumer", "c");

        if (consumerIds != null && !consumerIds.isEmpty()) {
            mainQuery.add(Restrictions.in("c.uuid", consumerIds));
        }

        if (owners != null && !owners.isEmpty()) {
            mainQuery.createAlias("c.owner", "o");
            mainQuery.add(Restrictions.in("o.key", owners));
        }

        if (startDate != null) {
            // Use greater than (not equals) because we've already looked up status for <= the start date
            // gte will open the door for duplicates
            mainQuery.add(Restrictions.gt("date", startDate));
        }

        if (endDate != null) {
            mainQuery.add(Restrictions.le("date", endDate));
        }

        snaps.addAll(mainQuery.list());
        return snaps;
    }

    /**
     * Retrieves the compliance status counts over the given time span. The counts are returned in a
     * map of maps, with the outer map mapping the dates to the inner map which maps the statuses to
     * their respective counts.
     *
     * @return
     *  a map of maps containing the compliance status counts, grouped by day. If no counts were
     *  found for the given time span, an empty map will be returned.
     */
    public Map<Date, Map<String, Long>> getComplianceStatusCounts() {
        return this.getComplianceStatusCounts(null, null, null, null, null);
    }

    /**
     * Retrieves the compliance status counts over the given time span. The counts are returned in a
     * map of maps, with the outer map mapping the dates to the inner map which maps the statuses to
     * their respective counts.
     * <p/>
     * If the start and/or end dates are null, the time span will be similarly unrestricted. Note
     * that the time within a given Date object is ignored. If neither the start nor end dates are
     * provided, all known compliance status data will be used.
     *
     * @param startDate
     *  <em>Optional</em><br/>
     *  The date at which the time span should begin. If null, all compliance statuses before the
     *  end date (if provided) will be used.
     *
     * @param endDate
     *  <em>Optional</em><br/>
     *  The date at which the time span should end. If null, all compliance statuses after the
     *  start date (if provided) will be used.
     *
     * @return
     *  a map of maps containing the compliance status counts, grouped by day. If no counts were
     *  found for the given time span, an empty map will be returned.
     */
    public Map<Date, Map<String, Long>> getComplianceStatusCounts(Date startDate, Date endDate) {
        return this.getComplianceStatusCounts(startDate, endDate, null, null, null);
    }

    /**
     * Retrieves the compliance status counts for consumers using subscriptions with the specified
     * sku over the given time span. The counts are returned in a map of maps, with the outer map
     * mapping the dates to the inner map which maps the statuses to their respective counts.
     * <p/>
     * If the start and/or end dates are null, the time span will be similarly unrestricted. Note
     * that the time within a given Date object is ignored. If neither the start nor end dates are
     * provided, all known compliance status data will be used.
     *
     * @param startDate
     *  <em>Optional</em><br/>
     *  The date at which the time span should begin. If null, all compliance statuses before the
     *  end date (if provided) will be used.
     *
     * @param endDate
     *  <em>Optional</em><br/>
     *  The date at which the time span should end. If null, all compliance statuses after the
     *  start date (if provided) will be used.
     *
     * @param sku
     *  <em>Optional</em><br/>
     *  A subscription sku to use to filter compliance status counts. If provided, only consumers
     *  using the specified sku will be counted.
     *
     * @return
     *  a map of maps containing the compliance status counts, grouped by day. If no counts were
     *  found for the given time span, an empty map will be returned.
     */
    public Map<Date, Map<String, Long>> getComplianceStatusCountsBySku(Date startDate, Date endDate,
        String sku) {
        return this.getComplianceStatusCounts(startDate, endDate, sku, null, null);
    }

    /**
     * Retrieves the compliance status counts for consumers using subscriptions with the specified
     * name over the given time span. The counts are returned in a map of maps, with the outer map
     * mapping the dates to the inner map which maps the statuses to their respective counts.
     * <p/>
     * If the start and/or end dates are null, the time span will be similarly unrestricted. Note
     * that the time within a given Date object is ignored. If neither the start nor end dates are
     * provided, all known compliance status data will be used.
     *
     * @param startDate
     *  <em>Optional</em><br/>
     *  The date at which the time span should begin. If null, all compliance statuses before the
     *  end date (if provided) will be used.
     *
     * @param endDate
     *  <em>Optional</em><br/>
     *  The date at which the time span should end. If null, all compliance statuses after the
     *  start date (if provided) will be used.
     *
     * @param subscriptionName
     *  <em>Optional</em><br/>
     *  A subscription name to use to filter compliance status counts. If provided, only consumers
     *  using subscriptions with the specified product name will be counted.
     *
     * @return
     *  a map of maps containing the compliance status counts, grouped by day. If no counts were
     *  found for the given time span, an empty map will be returned.
     */
    public Map<Date, Map<String, Long>> getComplianceStatusCountsBySubscription(Date startDate,
        Date endDate, String subscriptionName) {
        return this.getComplianceStatusCounts(startDate, endDate, null, subscriptionName, null);
    }

    /**
     * Retrieves the compliance status counts for consumers using subscriptions with the specified
     * name over the given time span. The counts are returned in a map of maps, with the outer map
     * mapping the dates to the inner map which maps the statuses to their respective counts.
     * <p/>
     * If the start and/or end dates are null, the time span will be similarly unrestricted. Note
     * that the time within a given Date object is ignored. If neither the start nor end dates are
     * provided, all known compliance status data will be used.
     *
     * @param startDate
     *  <em>Optional</em><br/>
     *  The date at which the time span should begin. If null, all compliance statuses before the
     *  end date (if provided) will be used.
     *
     * @param endDate
     *  <em>Optional</em><br/>
     *  The date at which the time span should end. If null, all compliance statuses after the
     *  start date (if provided) will be used.
     *
     * @param attributes
     *  <em>Optional</em><br/>
     *  A map of entitlement attributes to use to filter compliance status counts. If provided, only
     *  consumers with entitlements having the specified values for the given attributes will be
     *  counted.
     *
     * @return
     *  a map of maps containing the compliance status counts, grouped by day. If no counts were
     *  found for the given time span, an empty map will be returned.
     */
    public Map<Date, Map<String, Long>> getComplianceStatusCountsByAttributes(Date startDate, Date endDate,
        Map<String, String> attributes) {
        return this.getComplianceStatusCounts(startDate, endDate, null, null, attributes);
    }

    /**
     * Retrieves the compliance status counts over the given time span with the specified criteria.
     * The counts are returned in a map of maps, with the outer map mapping the dates to the inner
     * map which maps the statuses to their respective counts.
     * <p/>
     * If the start and/or end dates are null, the time span will be similarly unrestricted. Note
     * that the time within a given Date object is ignored. If neither the start nor end dates are
     * provided, all known compliance status data will be used.
     *
     * @param startDate
     *  <em>Optional</em><br/>
     *  The date at which the time span should begin. If null, all compliance statuses before the
     *  end date (if provided) will be used.
     *
     * @param endDate
     *  <em>Optional</em><br/>
     *  The date at which the time span should end. If null, all compliance statuses after the
     *  start date (if provided) will be used.
     *
     * @param sku
     *  <em>Optional</em><br/>
     *  A subscription sku to use to filter compliance status counts. If provided, only consumers
     *  using the specified sku will be counted.
     *
     * @param subscriptionName
     *  <em>Optional</em><br/>
     *  A subscription name to use to filter compliance status counts. If provided, only consumers
     *  using subscriptions with the specified product name will be counted.
     *
     * @param attributes
     *  <em>Optional</em><br/>
     *  A map of entitlement attributes to use to filter compliance status counts. If provided, only
     *  consumers with entitlements having the specified values for the given attributes will be
     *  counted.
     *
     * @return
     *  a map of maps containing the compliance status counts, grouped by day. If no counts were
     *  found for the given time span, an empty map will be returned.
     */
    private Map<Date, Map<String, Long>> getComplianceStatusCounts(Date startDate, Date endDate,
        String sku, String subscriptionName, Map<String, String> attributes) {

        // Build our query...
        Query query = this.buildComplianceStatusCountQuery(
            startDate,
            endDate,
            sku,
            subscriptionName,
            attributes
        );

        // Execute & process results...
        Map<Date, Map<String, Long>> resultmap = new HashMap<Date, Map<String, Long>>();
        Map<String, Long> statusmap;

        // Step through our data and do our manual aggregation bits...
        for (Object wrapper : query.list()) {
            Object[] row = (Object[]) wrapper;

            // Convert data...
            // Impl Note: Calendar's months are 0-indexed, so we have to decrement the values
            // we receive from the db.
            Calendar cal = Calendar.getInstance();
            cal.clear();
            cal.set(Calendar.YEAR, (Integer) row[0]);
            cal.set(Calendar.MONTH, ((Integer) row[1]).intValue() - 1);
            cal.set(Calendar.DAY_OF_MONTH, (Integer) row[2]);

            Date date = cal.getTime();
            String status = ((String) row[3]).toLowerCase();
            Long count = (Long) row[4];

            statusmap = resultmap.get(date);
            if (statusmap == null) {
                statusmap = new HashMap<String, Long>();
                resultmap.put(date, statusmap);
            }

            if (statusmap.containsKey(status)) {
                count += statusmap.get(status);
            }

            statusmap.put(status, count);
        }

        return resultmap;
    }

    /**
     * Builds the Query object to be used by the getComplianceStatusCounts method.
     * <p/>
     * The Query object is constructed with HQL translated from the following SQL:
     * <p/><pre>
     *  SELECT
     *    date_part('year', ComplianceSnap.date) AS year,
     *    date_part('month', ComplianceSnap.date) AS month,
     *    date_part('day', ComplianceSnap.date) AS day,
     *    ComplianceStatusSnap.status,
     *    count(ConsumerState.uuid)
     *
     *    FROM "gb_consumer_state" ConsumerState
     *
     *      INNER JOIN "gb_consumer_snap" ConsumerSnap
     *      ON ConsumerSnap.uuid = ConsumerState.uuid
     *
     *    INNER JOIN "gb_compliance_snap" ComplianceSnap
     *      ON ComplianceSnap.id = ConsumerSnap.compliance_snap_id
     *
     *    INNER JOIN "gb_compliance_status_snap" ComplianceStatusSnap
     *      ON ComplianceStatusSnap.compliance_snap_id = ComplianceSnap.id
     *
     *    WHERE (
     *      ConsumerState.deleted IS NULL
     *
     *      OR date_part('year', ComplianceSnap.date) < date_part('year', ConsumerState.deleted)
     *
     *      OR (
     *          date_part('year', ComplianceSnap.date) = date_part('year', ConsumerState.deleted)
     *          AND date_part('month', ComplianceSnap.date) < date_part('month', ConsumerState.deleted)
     *      )
     *
     *      OR (
     *          date_part('year', ComplianceSnap.date) = date_part('year', ConsumerState.deleted)
     *          AND date_part('month', ComplianceSnap.date) = date_part('month', ConsumerState.deleted)
     *          AND date_part('day', ComplianceSnap.date) < date_part('day', ConsumerState.deleted)
     *      ))
     *
     *      -- Selecting the max date for each day
     *      AND ComplianceSnap.id IN (
     *        SELECT TempB.id AS compliance_snap_id FROM
     *          (SELECT ConsumerState.uuid, MAX(ComplianceSnap.date) as date
     *            FROM "gb_consumer_state" ConsumerState
     *            INNER JOIN "gb_consumer_snap" ConsumerSnap
     *              ON ConsumerState.uuid = ConsumerSnap.uuid
     *            INNER JOIN "gb_compliance_snap" ComplianceSnap
     *              ON ComplianceSnap.id = ConsumerSnap.compliance_snap_id
     *
     *            GROUP BY
     *              ConsumerState.uuid,
     *              date_part('year', ComplianceSnap.date),
     *              date_part('month', ComplianceSnap.date),
     *              date_part('day', ComplianceSnap.date)
     *          ) AS TempA
     *
     *          INNER JOIN (
     *            SELECT ConsumerState.uuid, ComplianceSnap.date, ComplianceSnap.id
     *              FROM "gb_consumer_state" ConsumerState
     *              INNER JOIN "gb_consumer_snap" ConsumerSnap
     *                ON ConsumerState.uuid = ConsumerSnap.uuid
     *              INNER JOIN "gb_compliance_snap" ComplianceSnap
     *                ON ComplianceSnap.id = ConsumerSnap.compliance_snap_id
     *          ) AS TempB
     *
     *          ON TempA.uuid = TempB.uuid
     *          AND TempA.date = TempB.date
     *      )
     *
     *      -- Checking for the SKU, Product Name or attributes
     *      AND ComplianceSnap.id IN (
     *        SELECT ConsumerSnapI.compliance_snap_id
     *          FROM "gb_consumer_snap" ConsumerSnapI
     *          LEFT JOIN "gb_entitlement_snap" EntitlementSnap
     *            ON EntitlementSnap.compliance_snap_id = ConsumerSnapI.compliance_snap_id
     *
     *          LEFT JOIN "gb_ent_attr_snap" EntitlementAttributeSnap
     *            ON EntitlementAttributeSnap.ent_snap_id = EntitlementSnap.id
     *
     *          WHERE
     *            ConsumerSnapI.uuid = ConsumerSnap.uuid
     *            --AND (
     *            --  EntitlementSnap.product_id = User-input SKU
     *            --  OR EntitlementSnap.product_name = User-input name (matches-like?)
     *            --  OR (
     *            --    EntitlementAttributeSnap.gb_ent_attr_name = 'management_enabled'
     *            --    AND EntitlementAttributeSnap.gb_ent_attr_value = 1
     *            --  )
     *            --)
     *      )
     *
     *      -- In a date range
     *      --AND EntitlementSnap.product_id = SKU
     *      --AND EntitlementSnap.product_name LIKE <matches stuff?>
     *
     *    GROUP BY year, month, day, ComplianceStatusSnap.status
     *    ORDER BY year DESC, month DESC, day DESC
     *  </pre>
     *
     * @param startDate
     *  <em>Optional</em><br/>
     *  The date at which the time span should begin. If null, all compliance statuses before the
     *  end date (if provided) will be used.
     *
     * @param endDate
     *  <em>Optional</em><br/>
     *  The date at which the time span should end. If null, all compliance statuses after the
     *  start date (if provided) will be used.
     *
     * @param sku
     *  <em>Optional</em><br/>
     *  A subscription sku to use to filter compliance status counts. If provided, only consumers
     *  using the specified sku will be counted.
     *
     * @param subscriptionName
     *  <em>Optional</em><br/>
     *  A product name to use to filter compliance status counts. If provided, only consumers using
     *  subscriptions which provide the specified product name will be counted.
     *
     * @param attributes
     *  <em>Optional</em><br/>
     *  A map of entitlement attributes to use to filter compliance status counts. If provided, only
     *  consumers with entitlements having the specified values for the given attributes will be
     *  counted.
     *
     * @return
     *  A Query object to be used for retrieving compliance status counts.
     */
    @SuppressWarnings("checkstyle:methodlength")
    private Query buildComplianceStatusCountQuery(Date startDate, Date endDate,
        String sku, String subscriptionName, Map<String, String> attributes) {

        List<Object> parameters = new LinkedList<Object>();
        int counter = 0;

        StringBuilder hql = new StringBuilder(
            "SELECT " +
                "year(ComplianceSnap.date) AS year, " +
                "month(ComplianceSnap.date) AS month, " +
                "day(ComplianceSnap.date) AS day, " +
                "ComplianceStatusSnap.status, " +
                "COUNT(ConsumerState.uuid) " +

                "FROM Consumer AS ConsumerSnap " +
                "INNER JOIN ConsumerSnap.consumerState as ConsumerState " +
                "INNER JOIN ConsumerSnap.complianceSnapshot as ComplianceSnap " +
                "INNER JOIN ComplianceSnap.status as ComplianceStatusSnap " +

                "WHERE (" +
                        "ConsumerState.deleted IS NULL " +
                        "OR year(ComplianceSnap.date) < year(ConsumerState.deleted) " +
                        "OR (" +
                            "year(ComplianceSnap.date) = year(ConsumerState.deleted) " +
                            "AND month(ComplianceSnap.date) < month(ConsumerState.deleted) " +
                        ") " +
                        "OR (" +
                            "year(ComplianceSnap.date) = year(ConsumerState.deleted) " +
                            " AND month(ComplianceSnap.date) = month(ConsumerState.deleted) " +
                            " AND day(ComplianceSnap.date) < day(ConsumerState.deleted)" +
                        ")" +
                    ") " +

                    "AND ComplianceSnap.id IN (" +
                        "SELECT ComplianceSnap2.id " +
                            "FROM Consumer AS ConsumerSnap2 " +
                            "INNER JOIN ConsumerSnap2.consumerState as ConsumerState2 " +
                            "INNER JOIN ConsumerSnap2.complianceSnapshot as ComplianceSnap2 " +

                            "WHERE (ConsumerState2.uuid, ComplianceSnap2.date) IN (" +
                                "SELECT ConsumerState3.uuid, MAX(ComplianceSnap3.date) AS date " +
                                "FROM Consumer AS ConsumerSnap3 " +
                                "INNER JOIN ConsumerSnap3.consumerState as ConsumerState3 " +
                                "INNER JOIN ConsumerSnap3.complianceSnapshot as ComplianceSnap3 " +

                                "GROUP BY " +
                                    "ConsumerState3.uuid," +
                                    "year(ComplianceSnap3.date)," +
                                    "month(ComplianceSnap3.date)," +
                                    "day(ComplianceSnap3.date)" +
                            ")" +
                    ") "
        );

        // Add our reporting criteria...
        if (sku != null || subscriptionName != null || (attributes != null && attributes.size() > 0)) {
            List<String> criteria = new LinkedList<String>();
            StringBuffer inner = new StringBuffer(
                "AND ComplianceSnap.id IN (" +
                    "SELECT ComplianceSnap4.id " +
                        "FROM Consumer as ConsumerSnap4 " +
                        "INNER JOIN ConsumerSnap4.complianceSnapshot as ComplianceSnap4 " +
                        "INNER JOIN ComplianceSnap4.entitlements as EntitlementSnap4 " +

                        "WHERE " +
                            "ConsumerSnap4.uuid = ConsumerSnap.uuid " +
                            "AND ("
            );

            // TODO:
            // SKU and product name should be replaced by the same mechanism we used for --matches
            // in Subscription-manager.
            if (sku != null) {
                criteria.add("EntitlementSnap4.productId = ?" + ++counter);
                parameters.add(sku);
            }

            if (subscriptionName != null) {
                criteria.add("EntitlementSnap4.productName = ?" + ++counter);
                parameters.add(subscriptionName);
            }

            if (attributes != null) {
                for (Map.Entry<String, String> entry : attributes.entrySet()) {
                    criteria.add(String.format(
                        "(?%d, ?%d) IN (" +
                            "SELECT ENTRY(EntitlementSnapA.attributes) " +
                                "FROM Entitlement AS EntitlementSnapA " +
                                "WHERE EntitlementSnapA.id = EntitlementSnap4.id" +
                        ")",
                        ++counter,
                        ++counter
                    ));

                    parameters.add(entry.getKey());
                    parameters.add(entry.getValue());
                }
            }

            // Append the criteria to our where clause and close it.
            Iterator<String> ci = criteria.iterator();
            inner.append(ci.next());

            while (ci.hasNext()) {
                inner.append(" AND ");
                inner.append(ci.next());
            }

            hql.append(inner.append(")) "));
        }

        // Add our date range, if necessary...
        if (startDate != null) {
            // We want to use the entire start date, so we need to set the time to 00:00:00
            startDate = (Date) startDate.clone();
            startDate.setHours(0);
            startDate.setMinutes(0);
            startDate.setSeconds(0);

            hql.append("AND ComplianceSnap.date >= ?").append(++counter).append(' ');
            parameters.add(startDate);
        }

        if (endDate != null) {
            // We want to use the entire end date, so we need to set the time to 23:59:59
            endDate = (Date) endDate.clone();
            endDate.setHours(23);
            endDate.setMinutes(59);
            endDate.setSeconds(59);

            hql.append("AND ComplianceSnap.date <= ?").append(++counter).append(' ');
            parameters.add(endDate);
        }

        // Add our grouping...
        hql.append(
            "GROUP BY " +
                "year(ComplianceSnap.date), " +
                "month(ComplianceSnap.date), " +
                "day(ComplianceSnap.date), " +
                "ComplianceStatusSnap.status " +

            "ORDER BY " +
                "year DESC, " +
                "month DESC, " +
                "day DESC"
        );

        // Build our query object and set the parameters...
        Query query = this.currentSession().createQuery(hql.toString());

        for (int i = 1; i <= counter; ++i) {
            query.setParameter(String.valueOf(i), parameters.remove(0));
        }

        return query;
    }


}
