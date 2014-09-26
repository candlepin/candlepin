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
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Subqueries;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
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

        mainQuery.getExecutableCriteria(currentSession()).list();

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

}
