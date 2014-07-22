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

import org.candlepin.common.config.Configuration;
import org.candlepin.gutterball.config.ConfigKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.mongodb.MongoClient;

/**
 * A guice provider that creates a MongoClient from the Configuration.
 *
 */
public class MongoDBClientProvider implements Provider<MongoClient> {

    protected static final String DEFAULT_HOST = "localhost";
    protected static final int DEFAULT_PORT = 27017;

    private static Logger log = LoggerFactory.getLogger(MongoDBClientProvider.class);

    private MongoClient client;
    private String host;
    private int port;

    @Inject
    public MongoDBClientProvider(Configuration config) {
        host = config.getString(ConfigKey.MONGODB_HOST.toString(), DEFAULT_HOST);
        port = config.getInteger(ConfigKey.MONGODB_PORT.toString(), DEFAULT_PORT);
    }

    @Override
    public MongoClient get() {
        log.info("Creating mongodb connection: " + host + ":" + port);

        try {
            client = new MongoClient(host, port);
        }
        catch (UnknownHostException e) {
            throw new RuntimeException("Unable to connect to mongodb", e);
        }
        return client;
    }

    public void closeConnection() {
        log.info("Closing mongodb client instance.");
        if (this.client != null) {
            this.client.close();
        }
    }

}
