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

import java.util.LinkedList;
import java.util.List;

import org.candlepin.gutterball.mongodb.MongoConnection;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

import org.bson.types.ObjectId;

/**
 *
 * An abstract base class for gutterball curators. A curator is a wrapper
 * around a mongodb collection. DBObjects returned by the mongo java driver
 * can be safely casted to M.
 *
 * @param <M> a DBOject class representing the data stored in the collection.
 */
public abstract class MongoDBCurator<M extends DBObject> {

    protected DBCollection collection;

    public MongoDBCurator(Class<M> modelClass, MongoConnection mongo) {
        this.collection = mongo.getDB().getCollection(getCollectionName());
        this.collection.setObjectClass(modelClass);
    }

    /**
     * Defines the name of the collection to which this curator uses.
     * @return the name of the collection.
     */
    public abstract String getCollectionName();

    public DBCursor all() {
        return collection.find();
    }

    public void insert(M toInsert) {
        collection.insert(toInsert);
    }

    public void save(M toSave) {
        collection.save(toSave);
    }

    public M findById(String id) {
        return findByKey("_id", id);
    }

    public long count() {
        return collection.count();
    }

    @SuppressWarnings("unchecked")
    public M findByKey(String key, String value) {
        return (M) collection.findOne(new BasicDBObject(key, new ObjectId(value)));
    }

    protected List<ObjectId> getObjectIds(String fromKey, Iterable<DBObject> dbos) {
        List<ObjectId> results = new LinkedList<ObjectId>();
        for (DBObject obj : dbos) {
            results.add((ObjectId) obj.get(fromKey));
        }
        return results;
    }

    protected List<ObjectId> getObjectIds(Iterable<DBObject> dbos) {
        return getObjectIds("_id", dbos);
    }
}
