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

import org.candlepin.common.config.PropertyConverter;
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.gutterball.model.snapshot.Compliance;
import org.candlepin.gutterball.util.AutoEvictingColumnarResultsIterator;

import com.google.inject.Inject;

import org.hibernate.CacheMode;
import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Subqueries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;



/**
 * The curator responsible for managing {@link Compliance} objects.
 */
public class ComplianceSnapshotCurator extends BaseCurator<Compliance> {
    private static Logger log = LoggerFactory.getLogger(ComplianceSnapshotCurator.class);

    @Inject
    public ComplianceSnapshotCurator() {
        super(Compliance.class);
    }

    /**
     * Fetches the row count for the specified criteria. The criteria's projections and result
     * transformer will be reset in the process.
     *
     * @param criteria
     *  The Criteria for which to retrieve the row count.
     *
     * @return
     *  The number of rows returned from the database for the given Criteria-based query.
     */
    protected int getRowCount(Criteria criteria) {
        criteria.setProjection(Projections.rowCount());
        int count = ((Number) criteria.uniqueResult()).intValue();

        criteria.setProjection(null);
        criteria.setResultTransformer(Criteria.ROOT_ENTITY);

        return count;
    }

    /**
     * Retrieves an iterator over the compliance snapshots on the target date.
     *
     * @param targetDate
     *  The date for which to retrieve compliance snapshots. If null, the current date will be used
     *  instead.
     *
     * @param consumerUuids
     *  A list of consumer UUIDs to use to filter the results. If provided, only compliances for
     *  consumers in the list will be retrieved.
     *
     * @param ownerFilters
     *  A list of owners to use to filter the results. If provided, only compliances for consumers
     *  belonging to the specified owners (orgs) will be retrieved.
     *
     * @param statusFilters
     *  A list of statuses to use to filter the results. If provided, only compliances with a status
     *  matching the list will be retrieved.
     *
     * @return
     *  An iterator over the compliance snapshots for the target date.
     */
    public Iterator<Compliance> getSnapshotIterator(Date targetDate, List<String> consumerUuids,
        List<String> ownerFilters, List<String> statusFilters, Map<String, String> attributeFilters) {

        Page<Iterator<Compliance>> result = this.getSnapshotIterator(
            targetDate,
            consumerUuids,
            ownerFilters,
            statusFilters,
            attributeFilters,
            null
        );

        return result.getPageData();
    }

    /**
     * Retrieves an iterator over the compliance snapshots on the target date.
     *
     * @param targetDate
     *  The date for which to retrieve compliance snapshots. If null, the current date will be used
     *  instead.
     *
     * @param consumerUuids
     *  A list of consumer UUIDs to use to filter the results. If provided, only compliances for
     *  consumers in the list will be retrieved.
     *
     * @param ownerFilters
     *  A list of owners to use to filter the results. If provided, only compliances for consumers
     *  belonging to the specified owners (orgs) will be retrieved.
     *
     * @param statusFilters
     *  A list of statuses to use to filter the results. If provided, only compliances with a status
     *  matching the list will be retrieved.
     *
     * @param pageRequest
     *  A PageRequest instance containing paging information from the request. If null, no paging
     *  will be performed.
     *
     * @return
     *  A Page instance containing an iterator over the compliance snapshots for the target date and
     *  the paging information for the query.
     */
    public Page<Iterator<Compliance>> getSnapshotIterator(Date targetDate, List<String> consumerUuids,
        List<String> ownerFilters, List<String> statusFilters, Map<String, String> attributeFilters,
        PageRequest pageRequest) {

        Page<Iterator<Compliance>> page = new Page<Iterator<Compliance>>();
        page.setPageRequest(pageRequest);

        DetachedCriteria subquery = DetachedCriteria.forClass(Compliance.class);
        subquery.createAlias("consumer", "c");
        subquery.createAlias("c.consumerState", "state");

        // https://hibernate.atlassian.net/browse/HHH-2776
        if (consumerUuids != null && !consumerUuids.isEmpty()) {
            subquery.add(Restrictions.in("c.uuid", consumerUuids));
        }

        Date toCheck = targetDate == null ? new Date() : targetDate;
        subquery.add(Restrictions.or(
            Restrictions.isNull("state.deleted"),
            Restrictions.gt("state.deleted", toCheck)
        ));
        subquery.add(Restrictions.le("state.created", toCheck));

        if (ownerFilters != null && !ownerFilters.isEmpty()) {
            subquery.createAlias("c.owner", "o");
            subquery.add(Restrictions.in("o.key", ownerFilters));
        }

        subquery.add(Restrictions.le("date", toCheck));

        subquery.setProjection(
            Projections.projectionList()
                .add(Projections.max("date"))
                .add(Projections.groupProperty("c.uuid"))
        );

        // Post query filter on Status.
        Session session = this.currentSession();
        Criteria query = session.createCriteria(Compliance.class)
            .createAlias("consumer", "cs")
            .add(Subqueries.propertiesIn(new String[] {"date", "cs.uuid"}, subquery))
            .setCacheMode(CacheMode.IGNORE)
            .setReadOnly(true);


        if ((statusFilters != null && !statusFilters.isEmpty()) ||
                (attributeFilters != null && attributeFilters.containsKey("management_enabled"))) {
            query.createAlias("status", "stat");
            if (statusFilters != null && !statusFilters.isEmpty()) {
                query.add(Restrictions.in("stat.status", statusFilters));
            }

            if (attributeFilters != null && attributeFilters.containsKey("management_enabled")) {
                boolean managementEnabledFilter =
                        PropertyConverter.toBoolean(attributeFilters.get("management_enabled"));
                query.add(Restrictions.eq("stat.managementEnabled", managementEnabledFilter));
            }
        }

        if (pageRequest != null && pageRequest.isPaging()) {
            page.setMaxRecords(this.getRowCount(query));

            query.setFirstResult((pageRequest.getPage() - 1) * pageRequest.getPerPage());
            query.setMaxResults(pageRequest.getPerPage());

            if (pageRequest.getSortBy() != null) {
                query.addOrder(
                    pageRequest.getOrder() == PageRequest.Order.ASCENDING ?
                        Order.asc(pageRequest.getSortBy()) :
                        Order.desc(pageRequest.getSortBy())
                );
            }
        }

        page.setPageData(new AutoEvictingColumnarResultsIterator<Compliance>(
            session,
            query.scroll(ScrollMode.FORWARD_ONLY),
            0
        ));

        return page;
    }

    /**
     * Retrieves an iterator over the compliance snapshots for the specified consumer.
     *
     * @param consumerUUID
     *  The UUID for the consumer for which to retrieve compliance snapshots.
     *
     * @param startDate
     *  The start date to use to filter snapshots retrieved. If specified, only snapshots occurring
     *  after the start date, and the snapshot immediately preceding it, will be retrieved.
     *
     * @param endDate
     *  The end date to use to filter snapshots retrieved. If specified, only snapshots occurring
     *  before the end date will be retrieved.
     *
     * @return
     *  An iterator over the snapshots for the specified consumer.
     */
    public Iterator<Compliance> getSnapshotIteratorForConsumer(String consumerUUID, Date startDate,
        Date endDate) {

        Page<Iterator<Compliance>> result = this.getSnapshotIteratorForConsumer(
            consumerUUID,
            startDate,
            endDate,
            null
        );

        return result.getPageData();
    }

    /**
     * Retrieves an iterator over the compliance snapshots for the specified consumer.
     *
     * @param consumerUUID
     *  The UUID for the consumer for which to retrieve compliance snapshots.
     *
     * @param startDate
     *  The start date to use to filter snapshots retrieved. If specified, only snapshots occurring
     *  after the start date, and the snapshot immediately preceding it, will be retrieved.
     *
     * @param endDate
     *  The end date to use to filter snapshots retrieved. If specified, only snapshots occurring
     *  before the end date will be retrieved.
     *
     * @param pageRequest
     *  A PageRequest instance containing paging information from the request. If null, no paging
     *  will be performed.
     *
     * @return
     *  A Page instance containing an iterator over the snapshots for the specified consumer, and
     *  the paging information for the query.
     */
    public Page<Iterator<Compliance>> getSnapshotIteratorForConsumer(String consumerUUID, Date startDate,
        Date endDate, PageRequest pageRequest) {

        Page<Iterator<Compliance>> page = new Page<Iterator<Compliance>>();
        page.setPageRequest(pageRequest);

        Session session = this.currentSession();
        Criteria query = session.createCriteria(Compliance.class, "comp1");
        query.createAlias("comp1.consumer", "cons1");

        query.add(Restrictions.eq("cons1.uuid", consumerUUID));

        if (startDate != null) {
            DetachedCriteria subquery = DetachedCriteria.forClass(Compliance.class, "comp2");
            subquery.createAlias("comp2.consumer", "cons2");
            subquery.createAlias("cons2.consumerState", "state2");

            subquery.add(Restrictions.or(
                Restrictions.isNull("state2.deleted"),
                Restrictions.gt("state2.deleted", startDate)
            ));

            subquery.add(Restrictions.lt("state2.created", startDate));
            subquery.add(Restrictions.eqProperty("cons2.uuid", "cons1.uuid"));
            subquery.add(Restrictions.lt("comp2.date", startDate));

            subquery.setProjection(
                Projections.projectionList()
                    .add(Projections.max("comp2.date"))
            );

            query.add(
                Restrictions.disjunction()
                    .add(Restrictions.ge("comp1.date", startDate))
                    .add(Subqueries.propertyEq("comp1.date", subquery))
            );
        }

        if (endDate != null) {
            query.add(Restrictions.le("comp1.date", endDate));
        }

        query.setCacheMode(CacheMode.IGNORE);
        query.setReadOnly(true);

        if (pageRequest != null && pageRequest.isPaging()) {
            page.setMaxRecords(this.getRowCount(query));

            query.setFirstResult((pageRequest.getPage() - 1) * pageRequest.getPerPage());
            query.setMaxResults(pageRequest.getPerPage());

            if (pageRequest.getSortBy() != null) {
                query.addOrder(
                    pageRequest.getOrder() == PageRequest.Order.ASCENDING ?
                        Order.asc(pageRequest.getSortBy()) :
                        Order.desc(pageRequest.getSortBy())
                );
            }
        }

        page.setPageData(new AutoEvictingColumnarResultsIterator<Compliance>(
            session,
            query.scroll(ScrollMode.FORWARD_ONLY),
            0
        ));

        return page;
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
     *  The date at which the time span should begin. If null, all compliance statuses before the
     *  end date (if provided) will be used.
     *
     * @param endDate
     *  The date at which the time span should end. If null, all compliance statuses after the
     *  start date (if provided) will be used.
     *
     * @param sku
     *  A subscription sku to use to filter compliance status counts. If provided, only consumers
     *  using the specified sku will be counted.
     *
     * @param subscriptionName
     *  A subscription name to use to filter compliance status counts. If provided, only consumers
     *  using subscriptions with the specified product name will be counted.
     *
     * @param attributes
     *  A map of entitlement attributes to use to filter compliance status counts. If provided, only
     *  consumers with entitlements having the specified values for the given attributes will be
     *  counted.
     *
     * @param ownerKey
     *  An owner key to use to filter compliance status counts. If provided, only consumers
     *  associated with the specified owner key/account will be counted.
     *
     * @return
     *  a map of maps containing the compliance status counts, grouped by day. If no counts were
     *  found for the given time span, an empty map will be returned.
     */
    public Map<Date, Map<String, Integer>> getComplianceStatusCounts(Date startDate, Date endDate,
        String ownerKey, String sku, String subscriptionName, Map<String, String> attributes) {

        Page<Map<Date, Map<String, Integer>>> result = this.getComplianceStatusCounts(
            startDate,
            endDate,
            ownerKey,
            sku,
            subscriptionName,
            attributes,
            null
        );

        return result.getPageData();

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
     *  The date at which the time span should begin. If null, all compliance statuses before the
     *  end date (if provided) will be used.
     *
     * @param endDate
     *  The date at which the time span should end. If null, all compliance statuses after the
     *  start date (if provided) will be used.
     *
     * @param sku
     *  A subscription sku to use to filter compliance status counts. If provided, only consumers
     *  using the specified sku will be counted.
     *
     * @param subscriptionName
     *  A subscription name to use to filter compliance status counts. If provided, only consumers
     *  using subscriptions with the specified product name will be counted.
     *
     * @param attributes
     *  A map of entitlement attributes to use to filter compliance status counts. If provided, only
     *  consumers with entitlements having the specified values for the given attributes will be
     *  counted.
     *
     * @param ownerKey
     *  An owner key to use to filter compliance status counts. If provided, only consumers
     *  associated with the specified owner key/account will be counted.
     *
     * @param pageRequest
     *  A PageRequest instance containing paging information from the request. If null, no paging
     *  will be performed.
     *
     * @return
     *  A page containing a map of maps containing the compliance status counts, grouped by day. If
     *  no counts were found for the given time span, the page will contain an empty map.
     */
    public Page<Map<Date, Map<String, Integer>>> getComplianceStatusCounts(Date startDate, Date endDate,
        String ownerKey, String sku, String subscriptionName, Map<String, String> attributes,
        PageRequest pageRequest) {

        Page<Map<Date, Map<String, Integer>>> page = new Page<Map<Date, Map<String, Integer>>>();
        page.setPageRequest(pageRequest);

        // Build our query...
        // Impl note: This query's results MUST be sorted by date in ascending order. If it's not,
        // the algorithm below breaks.
        Query query = this.buildComplianceStatusCountQuery(
            this.currentSession(),
            startDate,
            endDate,
            ownerKey,
            sku,
            subscriptionName,
            attributes
        );

        // Clamp our dates so they're no further out than "today."
        Date today = new Date();
        if (startDate != null && startDate.after(today)) {
            startDate = today;
        }

        if (endDate != null && endDate.after(today)) {
            endDate = today;
        }

        // Execute & process results...
        Map<Date, Map<String, Integer>> resultmap = new TreeMap<Date, Map<String, Integer>>();
        Map<String, Object[]> cstatusmap = new HashMap<String, Object[]>();

        // Step through our data and do our manual aggregation bits...
        ScrollableResults results = query.scroll(ScrollMode.FORWARD_ONLY);

        if (results.next()) {
            Calendar date = Calendar.getInstance();

            Object[] row = results.get();
            String uuid = (String) row[0];
            row[1] = ((String) row[1]).toLowerCase();
            date.setTime((Date) row[2]);

            // Prime the calendars here...
            Calendar cdate = Calendar.getInstance();
            cdate.setTime(startDate != null ? startDate : date.getTime());
            cdate.set(Calendar.HOUR_OF_DAY, 23);
            cdate.set(Calendar.MINUTE, 59);
            cdate.set(Calendar.SECOND, 59);
            cdate.set(Calendar.MILLISECOND, 999);

            Calendar end = Calendar.getInstance();
            end.setTimeInMillis(endDate != null ? endDate.getTime() : Long.MAX_VALUE);

            for (; this.compareCalendarsByDate(cdate, end) <= 0; cdate.add(Calendar.DATE, 1)) {
                while (this.compareCalendarsByDate(date, cdate) <= 0) {
                    // Date is before our current date. Store the uuid's status so we can add it to
                    // our counts later.
                    cstatusmap.put(uuid, row);

                    if (!results.next()) {
                        if (endDate == null) {
                            end.setTimeInMillis(cdate.getTimeInMillis());
                        }

                        break;
                    }

                    row = (Object[]) results.get();
                    uuid = (String) row[0];
                    row[1] = ((String) row[1]).toLowerCase();
                    date.setTime((Date) row[2]);
                }

                Date hashdate = cdate.getTime();
                Map<String, Integer> statusmap = new HashMap<String, Integer>();

                // Go through and add up all our counts for the day.
                for (Object[] cstatus : cstatusmap.values()) {
                    if (cstatus[3] == null || this.compareDatesByDate(hashdate, (Date) cstatus[3]) < 0) {
                        Integer count = statusmap.get((String) cstatus[1]);
                        statusmap.put((String) cstatus[1], (count != null ? count + 1 : 1));
                    }
                }

                resultmap.put(hashdate, statusmap);
            }
        }

        results.close();

        // Pagination
        // This is horribly inefficient, but the only way to do it with the current implementation.
        if (pageRequest != null && pageRequest.isPaging()) {
            page.setMaxRecords(resultmap.size());

            int offset = (pageRequest.getPage() - 1) * pageRequest.getPerPage();
            int nextpage = offset + pageRequest.getPerPage();

            // Trim results. :(
            Iterator<Date> iterator = resultmap.keySet().iterator();
            for (int pos = 0; iterator.hasNext(); ++pos) {
                iterator.next();

                if (pos < offset || pos >= nextpage) {
                    iterator.remove();
                }
            }
        }

        page.setPageData(resultmap);
        return page;
    }

    /**
     * Builds the Query object to be used by the getComplianceStatusCounts method.
     * <p/>
     * The Query object is constructed with HQL translated from the following SQL:
     * <p/><pre>
     *  SELECT
     *    ConsumerState.uuid,
     *    ComplianceStatusSnap.status,
     *    ComplianceStatusSnap.date
     *
     *  FROM
     *    "gb_consumer_state" ConsumerState
     *
     *    INNER JOIN "gb_consumer_snap" ConsumerSnap
     *      ON ConsumerSnap.uuid = ConsumerState.uuid
     *
     *    INNER JOIN "gb_compliance_snap" ComplianceSnap
     *      ON ComplianceSnap.id = ConsumerSnap.compliance_snap_id
     *
     *    INNER JOIN "gb_compliance_status_snap" ComplianceStatusSnap
     *      ON ComplianceStatusSnap.compliance_snap_id = ComplianceSnap.id
     *
     *  WHERE (
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
     *      )
     *    )
     *
     *    AND (ComplianceStatusSnap.date, ConsumerSnap.uuid) IN (
     *      SELECT
     *        max(ComplianceSnap2.date) AS maxdate,
     *        ConsumerState2.uuid
     *
     *      FROM
     *        "gb_consumer_state" ConsumerState2
     *
     *        INNER JOIN "gb_consumer_snap" ConsumerSnap2
     *          ON ConsumerSnap2.uuid = ConsumerState2.uuid
     *
     *        INNER JOIN "gb_compliance_snap" ComplianceSnap2
     *          ON ComplianceSnap2.id = ConsumerSnap2.compliance_snap_id
     *
     *        INNER JOIN "gb_compliance_status_snap" ComplianceStatusSnap2
     *          ON ComplianceStatusSnap2.compliance_snap_id = ComplianceSnap2.id
     *
     *      GROUP BY
     *        date_part('year', ComplianceSnap2.date),
     *        date_part('month', ComplianceSnap2.date),
     *        date_part('day', ComplianceSnap2.date),
     *        ConsumerState2.uuid
     *    )
     *
     *    -- Min date
     *    AND (
     *      date_part('year', ComplianceSnap.date) > 2014
     *
     *      OR (
     *        date_part('year', ComplianceSnap.date) = 2014
     *        AND date_part('month', ComplianceSnap.date) > 11
     *      )
     *
     *      OR (
     *        date_part('year', ComplianceSnap.date) = 2014
     *        AND date_part('month', ComplianceSnap.date) = 11
     *        AND date_part('day', ComplianceSnap.date) >= 25
     *      )
     *
     *      OR (ComplianceStatusSnap.date, ConsumerSnap.uuid) IN (
     *        SELECT
     *          max(ComplianceSnap3.date) AS maxdate,
     *          ConsumerState3.uuid
     *
     *        FROM
     *          "gb_consumer_state" ConsumerState3
     *
     *          INNER JOIN "gb_consumer_snap" ConsumerSnap3
     *            ON ConsumerSnap3.uuid = ConsumerState3.uuid
     *
     *          INNER JOIN "gb_compliance_snap" ComplianceSnap3
     *            ON ComplianceSnap3.id = ConsumerSnap3.compliance_snap_id
     *
     *          INNER JOIN "gb_compliance_status_snap" ComplianceStatusSnap3
     *            ON ComplianceStatusSnap3.compliance_snap_id = ComplianceSnap3.id
     *
     *        WHERE
     *          date_part('year', ComplianceSnap3.date) < 2014
     *
     *          OR (
     *            date_part('year', ComplianceSnap3.date) = 2014
     *            AND date_part('month', ComplianceSnap3.date) < 11
     *          )
     *
     *          OR (
     *            date_part('year', ComplianceSnap3.date) = 2014
     *            AND date_part('month', ComplianceSnap3.date) = 11
     *            AND date_part('day', ComplianceSnap3.date) < 25
     *          )
     *
     *        GROUP BY
     *          ConsumerState3.uuid
     *      )
     *    )
     *
     *    -- Max date
     *    AND (
     *      date_part('year', ComplianceSnap.date) < 2014
     *
     *      OR (
     *        date_part('year', ComplianceSnap.date) = 2014
     *        AND date_part('month', ComplianceSnap.date) < 11
     *      )
     *
     *      OR (
     *        date_part('year', ComplianceSnap.date) = 2014
     *        AND date_part('month', ComplianceSnap.date) = 11
     *        AND date_part('day', ComplianceSnap.date) <= 15
     *      )
     *    )
     *
     *    -- Checking for the SKU, Product Name or attributes
     *    AND ComplianceSnap.id IN (
     *      SELECT ConsumerSnapI.compliance_snap_id
     *        FROM "gb_consumer_snap" ConsumerSnapI
     *        LEFT JOIN "gb_entitlement_snap" EntitlementSnap
     *          ON EntitlementSnap.compliance_snap_id = ConsumerSnapI.compliance_snap_id
     *
     *        LEFT JOIN "gb_ent_attr_snap" EntitlementAttributeSnap
     *          ON EntitlementAttributeSnap.ent_snap_id = EntitlementSnap.id
     *
     *        WHERE
     *          ConsumerSnapI.uuid = ConsumerSnap.uuid
     *          --AND (
     *          --  EntitlementSnap.product_id = User-input SKU
     *          --  OR EntitlementSnap.product_name = User-input name (matches-like?)
     *          --  OR (
     *          --    EntitlementAttributeSnap.gb_ent_attr_name = 'management_enabled'
     *          --    AND EntitlementAttributeSnap.gb_ent_attr_value = 1
     *          --  )
     *          --)
     *    )
     *
     *  ORDER BY
     *    ComplianceStatusSnap.date ASC
     *  </pre>
     *
     * @param session
     *  The session to use to create the query.
     *
     * @param startDate
     *  The date at which the time span should begin. If null, all compliance statuses before the
     *  end date (if provided) will be used.
     *
     * @param endDate
     *  The date at which the time span should end. If null, all compliance statuses after the
     *  start date (if provided) will be used.
     *
     * @param sku
     *  A subscription sku to use to filter compliance status counts. If provided, only consumers
     *  using the specified sku will be counted.
     *
     * @param subscriptionName
     *  A product name to use to filter compliance status counts. If provided, only consumers using
     *  subscriptions which provide the specified product name will be counted.
     *
     * @param attributes
     *  A map of entitlement attributes to use to filter compliance status counts. If provided, only
     *  consumers with entitlements having the specified values for the given attributes will be
     *  counted.
     *
     * @param ownerKey
     *  An owner key to use to filter compliance status counts. If provided, only consumers
     *  associated with the specified owner key/account will be counted.
     *
     * @return
     *  A Query object to be used for retrieving compliance status counts.
     */
    @SuppressWarnings("checkstyle:methodlength")
    private Query buildComplianceStatusCountQuery(Session session, Date startDate, Date endDate,
        String ownerKey, String sku, String subscriptionName, Map<String, String> attributes) {

        List<Object> parameters = new LinkedList<Object>();
        int counter = 0;

        StringBuilder hql = new StringBuilder(
            "SELECT " +
                "ConsumerState.uuid," +
                "ComplianceStatusSnap.status," +
                "ComplianceStatusSnap.date," +
                "ConsumerState.deleted " +

            "FROM " +
                "Consumer AS ConsumerSnap " +
                "INNER JOIN ConsumerSnap.consumerState AS ConsumerState " +
                "INNER JOIN ConsumerSnap.complianceSnapshot AS ComplianceSnap " +
                "INNER JOIN ComplianceSnap.status AS ComplianceStatusSnap " +
                "LEFT JOIN ComplianceSnap.entitlements AS EntitlementSnap " +

            "WHERE (" +
                    "ConsumerState.deleted IS NULL " +
                    "OR year(ComplianceSnap.date) < year(ConsumerState.deleted) " +
                    "OR (" +
                        "year(ComplianceSnap.date) = year(ConsumerState.deleted) " +
                        "AND month(ComplianceSnap.date) < month(ConsumerState.deleted) " +
                    ") " +
                    "OR (" +
                        "year(ComplianceSnap.date) = year(ConsumerState.deleted) " +
                        "AND month(ComplianceSnap.date) = month(ConsumerState.deleted) " +
                        "AND day(ComplianceSnap.date) < day(ConsumerState.deleted)" +
                    ")" +
                ") " +

                "AND (ComplianceStatusSnap.date, ConsumerSnap.uuid) IN (" +
                    "SELECT " +
                        "max(ComplianceSnap2.date) AS maxdate, " +
                        "ConsumerState2.uuid " +

                    "FROM " +
                        "Consumer AS ConsumerSnap2 " +
                        "INNER JOIN ConsumerSnap2.consumerState AS ConsumerState2 " +
                        "INNER JOIN ConsumerSnap2.complianceSnapshot AS ComplianceSnap2 " +
                        "INNER JOIN ComplianceSnap2.status AS ComplianceStatusSnap2 " +

                    "GROUP BY " +
                        "year(ComplianceSnap2.date)," +
                        "month(ComplianceSnap2.date)," +
                        "day(ComplianceSnap2.date)," +
                        "ConsumerState2.uuid " +
                ") "
        );

        // Add our reporting criteria...
        if (sku != null || subscriptionName != null || (attributes != null && attributes.size() > 0) ||
            ownerKey != null) {

            List<String> criteria = new LinkedList<String>();
            StringBuffer inner = new StringBuffer(
                "AND ("
            );

            // TODO:
            // Owner, SKU, product name and should be replaced by the same mechanism we used for
            // --matches in Subscription-manager.
            if (ownerKey != null) {
                criteria.add("ConsumerState.ownerKey = ?" + ++counter);
                parameters.add(ownerKey);
            }

            if (sku != null) {
                criteria.add("EntitlementSnap.productId = ?" + ++counter);
                parameters.add(sku);
            }

            if (subscriptionName != null) {
                criteria.add("EntitlementSnap.productName = ?" + ++counter);
                parameters.add(subscriptionName);
            }

            if (attributes != null) {

                if (attributes.containsKey("management_enabled")) {
                    boolean managementEnabledFilter =
                            PropertyConverter.toBoolean(attributes.get("management_enabled"));
                    criteria.add("ComplianceStatusSnap.managementEnabled = ?" + ++counter);
                    parameters.add(managementEnabledFilter);

                    // Don't process this attribute as part of entitlement attributes,
                    // as it has already been handled.
                    attributes.remove("management_enabled");
                }

                for (Map.Entry<String, String> entry : attributes.entrySet()) {
                    criteria.add(String.format(
                        "(?%d, ?%d) IN (" +
                            "SELECT ENTRY(EntitlementSnapA.attributes) " +
                                "FROM Entitlement AS EntitlementSnapA " +
                                "WHERE EntitlementSnapA.id = EntitlementSnap.id" +
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

            hql.append(inner.append(") "));
        }

        // Add our date range, if necessary...
        if (startDate != null) {
            int year = startDate.getYear() + 1900;
            int month = startDate.getMonth() + 1;
            int day = startDate.getDate();

            hql.append(String.format(
                "AND (" +
                    "year(ComplianceStatusSnap.date) > ?%1$d " +
                    "OR (" +
                        "year(ComplianceStatusSnap.date) = ?%1$d " +
                        "AND month(ComplianceStatusSnap.date) > ?%2$d" +
                    ") " +
                    "OR (" +
                        "year(ComplianceStatusSnap.date) = ?%1$d " +
                        " AND month(ComplianceStatusSnap.date) = ?%2$d " +
                        " AND day(ComplianceStatusSnap.date) >= ?%3$d" +
                    ")" +
                    "OR (ComplianceStatusSnap.date, ConsumerSnap.uuid) IN (" +
                        "SELECT " +
                            "max(ComplianceStatusSnap2.date) AS maxdate, " +
                            "ConsumerState2.uuid " +

                        "FROM " +
                            "Consumer AS ConsumerSnap2 " +
                            "INNER JOIN ConsumerSnap2.consumerState AS ConsumerState2 " +
                            "INNER JOIN ConsumerSnap2.complianceSnapshot AS ComplianceSnap2 " +
                            "INNER JOIN ComplianceSnap2.status AS ComplianceStatusSnap2 " +

                        "WHERE " +
                            "year(ComplianceStatusSnap2.date) < ?%1$d " +
                            "OR (" +
                                "year(ComplianceStatusSnap2.date) = ?%1$d " +
                                "AND month(ComplianceStatusSnap2.date) < ?%2$d" +
                            ") " +
                            "OR (" +
                                "year(ComplianceStatusSnap2.date) = ?%1$d " +
                                "AND month(ComplianceStatusSnap2.date) = ?%2$d " +
                                "AND day(ComplianceStatusSnap2.date) < ?%3$d" +
                            ") " +

                        "GROUP BY " +
                            "ConsumerState2.uuid" +
                    ")" +
                ") ",
                ++counter, ++counter, ++counter
            ));

            parameters.add(year);
            parameters.add(month);
            parameters.add(day);
        }

        if (endDate != null) {
            int year = endDate.getYear() + 1900;
            int month = endDate.getMonth() + 1;
            int day = endDate.getDate();

            hql.append(String.format(
                "AND (" +
                    "year(ComplianceStatusSnap.date) < ?%1$d " +
                    "OR (" +
                        "year(ComplianceStatusSnap.date) = ?%1$d " +
                        "AND month(ComplianceStatusSnap.date) < ?%2$d " +
                    ") " +
                    "OR (" +
                        "year(ComplianceStatusSnap.date) = ?%1$d " +
                        "AND month(ComplianceStatusSnap.date) = ?%2$d " +
                        "AND day(ComplianceStatusSnap.date) <= ?%3$d" +
                    ")" +
                ") ",
                ++counter, ++counter, ++counter
            ));

            parameters.add(year);
            parameters.add(month);
            parameters.add(day);
        }


        // Add our grouping...
        hql.append("ORDER BY ComplianceStatusSnap.date ASC");

        // Build our query object and set the parameters...
        Query query = session.createQuery(hql.toString());
        query.setReadOnly(true);

        for (int i = 1; i <= counter; ++i) {
            query.setParameter(String.valueOf(i), parameters.remove(0));
        }

        return query;
    }

    /**
     * Compares the date, without time, represented by the two Calendar objects.
     * <p/>
     * <strong>Note:</strong><br/>
     * This method <em>does not</em> check that the calendars provided are not null. Passing a null
     * calendar will result in a NullPointerException.
     *
     * @param cal1
     *  A Calendar instance to compare
     *
     * @param cal2
     *  A Calendar instance to compare
     *
     * @return
     *  0 if the dates are equal, a negative value if the date represented by cal1 is before cal2's,
     *  or a positive value if cal1's date is after cal2's.
     */
    private int compareCalendarsByDate(Calendar cal1, Calendar cal2) {
        int year = cal1.get(Calendar.YEAR) - cal2.get(Calendar.YEAR);
        int month = cal1.get(Calendar.MONTH) - cal2.get(Calendar.MONTH);
        int date = cal1.get(Calendar.DATE) - cal2.get(Calendar.DATE);

        return (year != 0 ? year : (month != 0 ? month : date));
    }

    /**
     * Compares the date, without time, represented by the two Date objects.
     * <p/>
     * <strong>Note:</strong><br/>
     * This method <em>does not</em> check that the dates provided are not null. Passing a null date
     * will result in a NullPointerException.
     *
     * @param date1
     *  A Date instance to compare
     *
     * @param date2
     *  A Date instance to compare
     *
     * @return
     *  0 if the dates are equal, a negative value if the date represented by date1 is before
     *  date2's, or a positive value if date1's date is after date2's.
     */
    private int compareDatesByDate(Date date1, Date date2) {
        int year = date1.getYear() - date2.getYear();
        int month = date1.getMonth() - date2.getMonth();
        int date = date1.getDate() - date2.getDate();

        return (year != 0 ? year : (month != 0 ? month : date));
    }


}
