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
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

import org.bson.types.ObjectId;

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

        // Create basic indexes for this collection.
        this.collection.createIndex(new BasicDBObject("consumer.uuid", 1));
        this.collection.createIndex(new BasicDBObject("consumer.owner.key", 1));
        this.collection.createIndex(new BasicDBObject("status.date", -1));
        this.collection.createIndex(new BasicDBObject("status.status", 1));

        // Setting the decoder factory may clobber the collections objectClass
        collection.setDBDecoderFactory(EscapingDBDecoder.FACTORY);
    }

    @Override
    public String getCollectionName() {
        return COLLECTION;
    }

    public DBCursor getComplianceOnDate(Date targetDate, List<String> consumerIds,
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

        // Build the projections
        BasicDBObject projections = new BasicDBObject();
        projections.put("_id", 1);
        projections.put("consumer.uuid", 1);
        projections.put("status.date", 1);
        BasicDBObject project = new BasicDBObject("$project", projections);

        // Build the result groups.
        BasicDBObject groups = new BasicDBObject("_id", "$consumer.uuid");
        groups.put("snapshot_id", new BasicDBObject("$first", "$_id"));

        DBObject query = new BasicDBObject("$match", queryBuilder.get());
        DBObject group = new BasicDBObject("$group", groups);
        DBObject sort = new BasicDBObject("$sort", new BasicDBObject("status.date", -1));

        // NOTE: The order of the aggregate actions is very important.
        AggregationOutput output = collection.aggregate(Arrays.asList(
            query, project, sort, group));

        // This query builder defines the post filters for the lookup. It looks up
        // all compliance snapshots by id as well as applies any post filtering required
        // such as status.
        //
        // The post filter should include any properties that are changeable, and are shared
        // amongst a snapshot record. For example, status will change often over time.
        BasicDBObjectBuilder filterQueryBuilder = BasicDBObjectBuilder.start();
        filterQueryBuilder.add("_id", new BasicDBObject("$in",
                getValuesByKey("snapshot_id", output.results())));

        // Filter results by status if required.
        if (statusFilers != null && !statusFilers.isEmpty()) {
            filterQueryBuilder.add("status.status", new BasicDBObject("$in", statusFilers));
        }

        DBCursor all = collection.find(filterQueryBuilder.get());
        // TODO Add paging support by using the max() and limit() methods of the DBCursor
        return all;
    }

    public DBCursor getComplianceForTimespan(Date startDate,
            Date endDate, List<String> consumerIds, List<String> owners) {
        // Anything added to the main query will filter the initial result set. This should
        // include anything that helps narrow down the query of the latest snapshots reported
        // and consists of properties that do not change across snapshots.
        BasicDBObjectBuilder queryBuilder = BasicDBObjectBuilder.start();


        if (owners != null && !owners.isEmpty()) {
            queryBuilder.add("consumer.owner.key", new BasicDBObject("$in", owners));
        }

        BasicDBObjectBuilder dateQueryBuilder = BasicDBObjectBuilder.start();
        if (startDate != null) {
            // Use greater than (not equals) because we've already looked up status for <= the start date
            // $gte will open the door for duplicates
            dateQueryBuilder.add("$gt", startDate);
        }
        if (endDate != null) {
            dateQueryBuilder.add("$lte", endDate);
        }
        if (startDate != null || endDate != null) {
            queryBuilder.add("status.date", dateQueryBuilder.get());
        }

        // Build the projections
        BasicDBObject projections = new BasicDBObject("_id", 1);
        BasicDBObject project = new BasicDBObject("$project", projections);

        DBObject query = new BasicDBObject("$match", queryBuilder.get());
        DBObject sort = new BasicDBObject("$sort", new BasicDBObject("status.date", 1));

        // NOTE: The order of the aggregate actions is very important.
        AggregationOutput output = collection.aggregate(Arrays.asList(
            query, project, sort/*, group, postResultFilter, skip, limit */));

        List<ObjectId> resultIds = getObjectIds(output.results());
        return collection.find(new BasicDBObject("_id", new BasicDBObject("$in", resultIds)));
    }
}
