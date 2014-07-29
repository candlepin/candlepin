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

package org.candlepin.gutterball.guice;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.candlepin.common.config.Configuration;
import org.candlepin.gutterball.config.ConfigKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.mongodb.MongoClient;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;

/**
 * A guice provider that creates a MongoClient from the Configuration.
 *
 */
public class MongoDBClientProvider implements Provider<MongoClient> {

    protected static final String DEFAULT_HOST = "localhost";
    protected static final int DEFAULT_PORT = 27017;

    private static Logger log = LoggerFactory.getLogger(MongoDBClientProvider.class);

    private MongoClient client;
    private ServerAddress mongoServerAddress;
    private List<MongoCredential> credentials;

    @Inject
    public MongoDBClientProvider(Configuration config) {
        String host = config.getString(ConfigKey.MONGODB_HOST.toString(), DEFAULT_HOST);
        int port = config.getInteger(ConfigKey.MONGODB_PORT.toString(), DEFAULT_PORT);
        String database = config.getString(ConfigKey.MONGODB_DATABASE.toString(),
                MongoDBProvider.DEFAULT_DB);
        String username = config.getString(ConfigKey.MONGODB_USERNAME.toString(), "");


        try {
            mongoServerAddress = new ServerAddress(host, port);
        }
        catch (UnknownHostException e) {
            throw new RuntimeException("Unable to connect to mongodb", e);
        }

        credentials = new ArrayList<MongoCredential>();
        if (username != null && !username.isEmpty()) {
            log.info("Mongodb connection will be authenticated with user: " + username);
            String password = config.getString(ConfigKey.MONGODB_PASSWORD.toString(), "");
            credentials.add(MongoCredential.createMongoCRCredential(username,
                database, password.toCharArray()));
        }
    }

    @Override
    public MongoClient get() {
        log.info("Creating mongodb connection: " + mongoServerAddress.getHost() +
                ":" + mongoServerAddress.getPort());
        client = new MongoClient(mongoServerAddress, credentials);
        return client;
    }

    public void closeConnection() {
        log.info("Closing mongodb client instance.");
        if (this.client != null) {
            this.client.close();
        }
    }

}
