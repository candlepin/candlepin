package org.candlepin.gutterball.curator.jpa;

import org.candlepin.gutterball.model.jpa.ComplianceSnapshot;

import com.google.inject.Inject;

import org.hibernate.Criteria;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Subqueries;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class ComplianceSnapshotCurator extends BaseCurator<ComplianceSnapshot> {

    private ConsumerStateCurator consumerStateCurator;

    @Inject
    public ComplianceSnapshotCurator(ConsumerStateCurator consumerStateCurator) {
        super(ComplianceSnapshot.class);
        this.consumerStateCurator = consumerStateCurator;
    }

    public List<ComplianceSnapshot> getSnapshotsOnDate(
            Date targetDate, List<String> consumerUuids,
            List<String> ownerFilters, List<String> statusFilters) {

        List<String> activeConsumers =
                consumerStateCurator.getConsumerUuidsOnDate(targetDate, ownerFilters, consumerUuids);

        // https://hibernate.atlassian.net/browse/HHH-2776
        if (activeConsumers == null || activeConsumers.isEmpty()) {
            return new ArrayList<ComplianceSnapshot>();
        }

        DetachedCriteria mainQuery = DetachedCriteria.forClass(ComplianceSnapshot.class);
        mainQuery.createAlias("consumerSnapshot", "c");

        if (ownerFilters != null && !ownerFilters.isEmpty()) {
            mainQuery.createAlias("c.ownerSnapshot", "os");
            mainQuery.add(Restrictions.in("os.key", ownerFilters));
        }

        Date toCheck = targetDate == null ? new Date() : targetDate;
        mainQuery.add(Restrictions.le("date", toCheck));

        mainQuery.setProjection(
            Projections.projectionList()
                .add(Projections.max("date"))
                .add(Projections.groupProperty("c.uuid"))
        );

        // Post query filter on Status.
        Criteria postFilter = currentSession().createCriteria(ComplianceSnapshot.class)
            .createAlias("consumerSnapshot", "consumer")
            .add(Subqueries.propertiesIn(new String[] {"date", "consumer.uuid"}, mainQuery));

        if (statusFilters != null && !statusFilters.isEmpty()) {
            postFilter.createAlias("complianceStatusSnapshot", "status");
            postFilter.add(Restrictions.in("status.status", statusFilters));
        }

        return postFilter.list();
    }

    public Set<ComplianceSnapshot> getComplianceForTimespan(Date startDate, Date endDate,
            List<String> consumerIds, List<String> owners) {

        // If the start date is null, we can return all status updates.
        // Otherwise, we need to get every consumers latest compliance info at that point.
        Set<ComplianceSnapshot> snaps = new HashSet<ComplianceSnapshot>();
        if (startDate != null) {
            // Don't restrict by status here, it may not match to begin with, we only care if it matches
            snaps.addAll(getSnapshotsOnDate(startDate, consumerIds, owners, null));
        }

        Criteria mainQuery = currentSession().createCriteria(ComplianceSnapshot.class);
        mainQuery.createAlias("consumerSnapshot", "c");

        if (consumerIds != null && !consumerIds.isEmpty()) {
            mainQuery.add(Restrictions.in("c.uuid", consumerIds));
        }

        if (owners != null && !owners.isEmpty()) {
            mainQuery.createAlias("c.ownerSnapshot", "os");
            mainQuery.add(Restrictions.in("os.key", owners));
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

}
