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

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.gutterball.config.Configuration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.mongodb.MongoClient;

@RunWith(MockitoJUnitRunner.class)
public class MongoDBProviderTest {

    private static final String TEST_DB = "TEST_DB";

    @Mock
    private MongoClient mongoClient;

    @Mock
    private Configuration config;

    @Before
    public void setupMocks() {
        when(config.getString(eq(MongoDBProvider.DATABASE_CONFIG_PROPERTY),
                              eq(MongoDBProvider.DEFAULT_DATABASE)))
                              .thenReturn(TEST_DB);
    }

    @Test
    public void testCorrectDatabaseConfigured() {
        new MongoDBProvider(config, mongoClient);
        verify(config).getString(eq(MongoDBProvider.DATABASE_CONFIG_PROPERTY),
                              eq(MongoDBProvider.DEFAULT_DATABASE));
        verify(mongoClient).getDB(eq(TEST_DB));
    }

}
