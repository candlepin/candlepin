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

import javax.inject.Inject;

import org.candlepin.common.config.Configuration;
import org.candlepin.gutterball.config.ConfigKey;

import com.google.inject.Provider;
import com.mongodb.DB;
import com.mongodb.MongoClient;

/**
 * A guice provider that provides a connection to a mongo DB database.
 *
 */
public class MongoDBProvider implements Provider<DB> {

    protected static final String DEFAULT_DB = "gutterball";

    private DB database;

    @Inject
    public MongoDBProvider(Configuration config, MongoClient mongo) {
        String databaseName = config.getString(ConfigKey.MONGODB_DATABASE.toString(),
                DEFAULT_DB);
        database = mongo.getDB(databaseName);
    }

    @Override
    public DB get() {
        return database;
    }

}
