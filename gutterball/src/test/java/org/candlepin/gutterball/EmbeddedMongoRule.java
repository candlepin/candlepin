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

package org.candlepin.gutterball;

import org.candlepin.common.config.Configuration;
import org.candlepin.common.config.ConfigurationException;
import org.candlepin.common.config.MapConfiguration;
import org.candlepin.common.config.PropertiesFileConfiguration;
import org.candlepin.gutterball.config.ConfigProperties;
import org.candlepin.gutterball.mongodb.MongoConnection;

import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.Charset;

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;

/**
 * Adding this junit rule to a test class will embed a mongodb
 * instance and will create a connection to it. Tests can access
 * the connection in setup/teardowns.
 */
public class EmbeddedMongoRule extends ExternalResource {
    private static final String EMBEDDED_MONGO_OVERRIDES = "embedded-mongo.properties";
    private static final int TEST_PORT = 12345;
    private static final String TEST_DATABASE = "gutterball-test";

    private static final MongodStarter STARTER = MongodStarter.getDefaultInstance();
    private static Logger log = LoggerFactory.getLogger(EmbeddedMongoRule.class);

    private MongodExecutable mongodExe;
    private MongodProcess mongod;
    private MongoConnection connection;
    private Configuration config;


    public EmbeddedMongoRule() {
        // Allow mongodb config overrides via properties file.
        Configuration config = readConfigOverride();
        if (config == null) {
            config = new MapConfiguration();
            config.setProperty(ConfigProperties.MONGODB_PORT, TEST_PORT);
            config.setProperty(ConfigProperties.MONGODB_DATABASE, TEST_DATABASE);
        }
        this.config = config;
    }

    @Override
    protected void after() {
        log.info("Shutting down embedded mongodb server...");
        connection.close();
        mongod.stop();
        mongodExe.stop();
    }

    @Override
    protected void before() throws Throwable {
        log.info("Starting embedded mongodb server...");
        mongodExe = STARTER.prepare(new MongodConfigBuilder()
            .version(Version.Main.PRODUCTION)
            .net(new Net(config.getInteger(ConfigProperties.MONGODB_PORT),
                    Network.localhostIsIPv6()))
            .build());
        mongod = mongodExe.start();

        connection = new MongoConnection(config);
    }

    public MongoConnection getMongoConnection() {
        return connection;
    }

    private Configuration readConfigOverride() {
        Charset utf8 = Charset.forName("UTF-8");
        InputStream defaultStream = EmbeddedMongoRule.class
                .getClassLoader().getResourceAsStream(EMBEDDED_MONGO_OVERRIDES);
        if (defaultStream == null) {
            return null;
        }

        Configuration defaults = null;
        try {
            defaults = new PropertiesFileConfiguration(defaultStream, utf8);
        }
        catch (ConfigurationException e) {
            return null;
        }
        return defaults;
    }
}
