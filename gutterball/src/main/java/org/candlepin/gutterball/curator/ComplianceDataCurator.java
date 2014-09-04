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
import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MapReduceCommand;
import com.mongodb.MapReduceOutput;

import org.bson.types.ObjectId;

import java.util.Date;
import java.util.LinkedList;
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

        String mapFunction =
            "function () {" +
            "  emit(this.consumer.uuid, {'id': this._id, 'date': this.status.date});" +
            "}";

        String reduceFunction =
            "function (consumerUuid, statusInfo) {" +
            "  var selected = null;" +
            "  for (var i = 0; i < statusInfo.length; i++) {" +
            "    var status = statusInfo[i];" +
            "    if (selected == null || status.date > selected.date) { selected = status; }" +
            "  }" +
            "  return selected;" +
            "}";

        MapReduceCommand command = new MapReduceCommand(collection, mapFunction, reduceFunction,
                null, MapReduceCommand.OutputType.INLINE, queryBuilder.get());

        List<ObjectId> ids = new LinkedList<ObjectId>();
        MapReduceOutput output = collection.mapReduce(command);
        for (DBObject row : output.results()) {
            DBObject value = (DBObject) row.get("value");
            ids.add((ObjectId) value.get("id"));
        }

        // This query builder defines the post filters for the lookup. It looks up
        // all compliance snapshots by id as well as applies any post filtering required
        // such as status.
        //
        // The post filter should include any properties that are changeable, and are shared
        // amongst a snapshot record. For example, status will change often over time.
        BasicDBObjectBuilder filterQueryBuilder = BasicDBObjectBuilder.start();
        filterQueryBuilder.add("_id", new BasicDBObject("$in", ids));

        // Filter results by status if required.
        if (statusFilers != null && !statusFilers.isEmpty()) {
            filterQueryBuilder.add("status.status", new BasicDBObject("$in", statusFilers));
        }

        DBCursor all = collection.find(filterQueryBuilder.get());
        // TODO Add paging support by using the max() and limit() methods of the DBCursor
        return all;
    }

    public Iterable<DBObject> getComplianceForTimespan(Date startDate,
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
        BasicDBObject projections = new BasicDBObject();
        projections.put("consumer", 1);
        projections.put("status", 1);
        projections.put("_id", 0);
        BasicDBObject project = new BasicDBObject("$project", projections);

        DBObject query = new BasicDBObject("$match", queryBuilder.get());
        DBObject sort = new BasicDBObject("$sort", new BasicDBObject("status.date", 1));

        // NOTE: The order of the aggregate actions is very important.
        AggregationOutput output = collection.aggregate(Arrays.asList(
            query, project, sort/*, group, postResultFilter, skip, limit */));
        return output.results();
    }
}
