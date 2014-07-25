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

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.candlepin.common.config.Configuration;
import org.candlepin.gutterball.config.ConfigKey;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.MongoCredential;
import com.mongodb.MongoException;
import com.mongodb.ServerAddress;

@RunWith(MockitoJUnitRunner.class)
public class MongoConnectionTest {

    @Mock
    private Configuration config;

    @Test
    public void testProperConfigValuesUsedForNoAuth() {
        when(config.getString(ConfigKey.MONGODB_USERNAME.toString(), ""))
            .thenReturn("");

        new MongoConnectionStub(config);

        verifyCommonConfig();
        verify(config, never()).getString(ConfigKey.MONGODB_PASSWORD.toString(), "");
    }

    @Test
    public void testProperConfigValuesUserForAuthEnabled() {
        when(config.getString(ConfigKey.MONGODB_USERNAME.toString(), ""))
            .thenReturn("username");
        when(config.getString(ConfigKey.MONGODB_PASSWORD.toString(), ""))
            .thenReturn("password");

        new MongoConnectionStub(config);

        verifyCommonConfig();
        verify(config).getString(eq(ConfigKey.MONGODB_PASSWORD.toString()), eq(""));
    }

    private void verifyCommonConfig() {
        verify(config).getString(eq(ConfigKey.MONGODB_HOST.toString()),
                eq(MongoConnection.DEFAULT_HOST));
        verify(config).getInteger(eq(ConfigKey.MONGODB_PORT.toString()),
                eq(MongoConnection.DEFAULT_PORT));
        verify(config).getString(eq(ConfigKey.MONGODB_DATABASE.toString()),
                eq(MongoConnection.DEFAULT_DB));
    }

    @After
    public void validate() {
        validateMockitoUsage();
    }

    private class MongoConnectionStub extends MongoConnection {

        public MongoConnectionStub(Configuration config) throws MongoException {
            super(config);
        }

        /**
         * Override to stub out connection details
         */
        @Override
        protected void initConnection(ServerAddress address,
            List<MongoCredential> credentials, String databaseName) throws MongoException {
            this.mongo = Mockito.mock(MongoClient.class);
        }


    }
}
