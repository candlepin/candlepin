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

    private ConsumerCurator consumerCurator;

    @Inject
    public ComplianceDataCurator(MongoConnection mongo, ConsumerCurator consumerCurator) {
        super(BasicDBObject.class, mongo);
        this.consumerCurator = consumerCurator;
    }

    @Override
    public String getCollectionName() {
        return COLLECTION;
    }

    public Iterable<DBObject> getComplianceOnDate(Date targetDate, List<String> consumerIds,
            List<String> owners, List<String> statusFilers) {

        // Anything added to the main query will filter the initial result set. This should
        // include anything that helps narrow down the query of the latest snapshots reported
        // and consists of properties that do not change across snapshots.
        BasicDBObjectBuilder queryBuilder = BasicDBObjectBuilder.start();

        queryBuilder.add("consumer.uuid", new BasicDBObject("$in",
                consumerCurator.getUuidsOnDate(targetDate, owners, consumerIds)));

        if (owners != null && !owners.isEmpty()) {
            queryBuilder.add("consumer.owner.key", new BasicDBObject("$in", owners));
        }

        queryBuilder.add("status.date", new BasicDBObject("$lte", targetDate));

        // The filter aggregate is a post result filter that is applied to the set of
        // snapshots returned by the query. The post filter should include any properties that
        // are changable, and are shared amongst a snapshot record. For example, status will change
        // often over time.
        //
        // This post filtering is required since the initial query could match on a snapshot
        // that was not the consumer's latest snapshot record.
        BasicDBObjectBuilder filterBuilder = BasicDBObjectBuilder.start();
        if (statusFilers != null && !statusFilers.isEmpty()) {
            filterBuilder.add("status.status", new BasicDBObject("$in", statusFilers));
        }

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

        DBObject query = new BasicDBObject("$match", queryBuilder.get());
        DBObject postResultFilter = new BasicDBObject("$match", filterBuilder.get());
        DBObject group = new BasicDBObject("$group", groups);
        DBObject sort = new BasicDBObject("$sort", new BasicDBObject("status.date", -1));

        // TODO Support paging.
        // DBObject limit = new BasicDBObject("$limit", 10);
        // DBObject skip = new BasicDBObject("$skip", 1);

        // NOTE: The order of the aggregate actions is very important.
        AggregationOutput output = collection.aggregate(Arrays.asList(
            query, project, sort, group, postResultFilter /* skip, limit */));
        return output.results();
    }
}
