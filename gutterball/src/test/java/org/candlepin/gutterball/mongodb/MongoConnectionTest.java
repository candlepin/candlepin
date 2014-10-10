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

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.common.config.Configuration;
import org.candlepin.gutterball.config.ConfigProperties;

import com.mongodb.MongoClient;
import com.mongodb.MongoCredential;
import com.mongodb.MongoException;
import com.mongodb.ServerAddress;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class MongoConnectionTest {

    @Mock
    private Configuration config;

    @Test
    public void testProperConfigValuesUsedForNoAuth() {
        when(config.getString(ConfigProperties.MONGODB_USERNAME, ""))
            .thenReturn("");

        new MongoConnectionStub(config);

        verifyCommonConfig();
        verify(config, never()).getString(ConfigProperties.MONGODB_PASSWORD, "");
    }

    @Test
    public void testProperConfigValuesUserForAuthEnabled() {
        when(config.getString(ConfigProperties.MONGODB_USERNAME, ""))
            .thenReturn("username");
        when(config.getString(ConfigProperties.MONGODB_PASSWORD, ""))
            .thenReturn("password");

        new MongoConnectionStub(config);

        verifyCommonConfig();
        verify(config).getString(eq(ConfigProperties.MONGODB_PASSWORD), eq(""));
    }

    private void verifyCommonConfig() {
        verify(config).getString(eq(ConfigProperties.MONGODB_HOST),
                eq(MongoConnection.DEFAULT_HOST));
        verify(config).getInt(eq(ConfigProperties.MONGODB_PORT),
                eq(MongoConnection.DEFAULT_PORT));
        verify(config).getString(eq(ConfigProperties.MONGODB_DATABASE),
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
