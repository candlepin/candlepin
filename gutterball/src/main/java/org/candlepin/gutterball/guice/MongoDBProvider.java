package org.candlepin.gutterball.guice;

import javax.inject.Inject;

import org.candlepin.gutterball.config.Configuration;

import com.google.inject.Provider;
import com.mongodb.DB;
import com.mongodb.MongoClient;

public class MongoDBProvider implements Provider<DB> {
	public static String DEFAULT_DATABASE = "gutterball";
	
	private DB database;

	@Inject
	public MongoDBProvider(Configuration config, MongoClient mongo) {
		String databaseName = config.getString("gutterball.mongodb.database", DEFAULT_DATABASE);
		database = mongo.getDB(databaseName);
	}
	
	@Override
	public DB get() {
		return database;
	}

}
