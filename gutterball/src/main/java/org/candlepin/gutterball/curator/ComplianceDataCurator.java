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

import org.candlepin.gutterball.mongodb.MongoConnection;

import com.google.inject.Inject;
import com.mongodb.AggregationOutput;
import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * ComplianceDataCurator database curator to save and query
 * data from compliance events.
 */
public class ComplianceDataCurator extends MongoDBCurator<BasicDBObject> {

    public static final String COLLECTION = "compliance";

    @Inject
    public ComplianceDataCurator(MongoConnection mongo) {
        super(BasicDBObject.class, mongo);
    }

    @Override
    public String getCollectionName() {
        return COLLECTION;
    }

    public Iterable<DBObject> getComplianceForTimespan(Date startDate, Date endDate,
            List<String> consumerIds, List<String> owners, List<String> statusFilers) {

        // Build the match statement
        BasicDBObjectBuilder queryBuilder = BasicDBObjectBuilder.start();
        if (consumerIds != null && !consumerIds.isEmpty()) {
            queryBuilder.add("consumer.uuid", new BasicDBObject("$in", consumerIds));
        }

        if (owners != null && !owners.isEmpty()) {
            queryBuilder.add("consumer.owner.key", new BasicDBObject("$in", owners));
        }

        if (statusFilers != null && !statusFilers.isEmpty()) {
            queryBuilder.add("status.status", new BasicDBObject("$in", statusFilers));
        }

        BasicDBObject statusDateCriteria = new BasicDBObject();
        if (endDate == null) {
            // Search all latest status records.
            statusDateCriteria.append("$lte", startDate);
        }
        else {
            boolean flip = startDate.after(endDate);
            String startDateFilter = flip ? "$lte" : "$gte";
            String endDateFilter = flip ? "$gte" : "$lte";

            statusDateCriteria.append(startDateFilter, startDate);
            statusDateCriteria.append(endDateFilter, endDate);
        }

        queryBuilder.add("status.date", statusDateCriteria);

        // Build the projections
        BasicDBObject projections = new BasicDBObject();
        projections.put("consumer", 1);
        projections.put("status", 1);
        projections.put("_id", 0);
        BasicDBObject project = new BasicDBObject("$project", projections);

        // Build the result groups.
        BasicDBObject groups = new BasicDBObject("_id", "$consumer.uuid");
        groups.put("consumer", new BasicDBObject("$first", "$consumer"));
        groups.put("status", new BasicDBObject("$first", "$status"));


        DBObject match = new BasicDBObject("$match", queryBuilder.get());
        DBObject group = new BasicDBObject("$group", groups);
        DBObject sort = new BasicDBObject("$sort", new BasicDBObject("status.date", -1));

        // TODO Support paging.
        // DBObject limit = new BasicDBObject("$limit", 10);
        // DBObject skip = new BasicDBObject("$skip", 1);

        AggregationOutput output = collection.aggregate(Arrays.asList(match, project, sort, group));
        return output.results();
    }

    public Iterable<DBObject> getComplianceForAllConsumers(
            List<String> consumerIds, List<String> ownerFilters,
            List<String> statusFilters) {
        return this.getComplianceForTimespan(new Date(), null, consumerIds, ownerFilters, statusFilters);
    }
}
