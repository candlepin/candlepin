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

package org.candlepin.gutterball.mongodb;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.candlepin.common.config.Configuration;
import org.candlepin.gutterball.config.ConfigKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.MongoCredential;
import com.mongodb.MongoException;
import com.mongodb.ServerAddress;

/**
 * Encapsulates the MongoDB Java driver connection details. All configuration is
 * done in this class.
 *
 */
public class MongoConnection {

    protected static final String DEFAULT_HOST = "localhost";
    protected static final int DEFAULT_PORT = 27017;
    protected static final String DEFAULT_DB = "gutterball";

    private static Logger log = LoggerFactory.getLogger(MongoConnection.class);

    protected MongoClient mongo;
    private String databaseName;

    public MongoConnection(Configuration config) throws MongoException {
        String host = config.getString(ConfigKey.MONGODB_HOST.toString(), DEFAULT_HOST);
        int port = config.getInteger(ConfigKey.MONGODB_PORT.toString(), DEFAULT_PORT);
        databaseName = config.getString(ConfigKey.MONGODB_DATABASE.toString(),
                DEFAULT_DB);
        String username = config.getString(ConfigKey.MONGODB_USERNAME.toString(), "");

        ServerAddress serverAddress = null;
        try {
            serverAddress = new ServerAddress(host, port);
        }
        catch (UnknownHostException e) {
            throw new MongoException("Unable to connect to mongodb", e);
        }

        List<MongoCredential> credentials = new ArrayList<MongoCredential>();
        if (username != null && !username.isEmpty()) {
            log.info("Mongodb connection will be authenticated with user: " + username);
            String password = config.getString(ConfigKey.MONGODB_PASSWORD.toString(), "");
            credentials.add(MongoCredential.createMongoCRCredential(username,
                databaseName, password.toCharArray()));
        }
        initConnection(serverAddress, credentials, databaseName);
    }

    protected void initConnection(ServerAddress address, List<MongoCredential> credentials,
        String databaseName) throws MongoException {
        mongo = new MongoClient(address, credentials);
        DB dbConnection = mongo.getDB(databaseName);

        // Check to make sure that our connection details are correct.
        // If anything is wrong, this will throw a MongoException.
        dbConnection.getCollectionNames();
    }

    public MongoClient getMongoClient() {
        return mongo;
    }

    public DB getDB() {
        return mongo.getDB(databaseName);
    }

    public void close() {
        mongo.close();
    }

}
