package org.candlepin.gutterball.guice;

import java.net.UnknownHostException;

import org.candlepin.gutterball.config.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.mongodb.MongoClient;

public class MongoDBClientProvider implements Provider<MongoClient> {
	
	private static String DEFAULT_HOST = "localhost";
	private static int DEFAULT_PORT = 27017;
	
	private static Logger log = LoggerFactory.getLogger(MongoDBClientProvider.class);
	
	private MongoClient client;
	private String host;
	private int port;
	
	@Inject
	public MongoDBClientProvider(Configuration config) {
		host = config.getString("gutterball.mongodb.host", DEFAULT_HOST);
		port = config.getInteger("gutterball.mongodb.port", DEFAULT_PORT);
		
		this.client = createConnection();
	}

	protected MongoClient createConnection() {
		log.info("Creating mongodb connection: " + host + ":" + port);
		
		try {
			return new MongoClient(host, port);
		}
		catch (UnknownHostException e) {
			throw new RuntimeException("Unable to connect to mongodb", e);
		}
	}
	
	@Override
	public MongoClient get() {
		log.info("Retrieving new mongodb client instance");
		return this.client;
	}
	
	public void closeConnection() {
		this.client.close();
	}

}
