package org.candlepin.gutterball.curator;

import org.bson.types.ObjectId;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

public abstract class MongoDBCurator<M extends DBObject> {

    private DBCollection collection;

    public MongoDBCurator(Class<M> modelClass, DB database) {
    	this.collection = database.getCollection(getCollectionName());
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
        return (M) collection.findOne(new BasicDBObject("_id", new ObjectId(id)));
    }
    
    public long count() {
    	return collection.count();
    }

}
