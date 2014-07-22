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
import static org.mockito.Mockito.validateMockitoUsage;


import org.candlepin.common.config.Configuration;
import org.candlepin.gutterball.config.ConfigKey;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MongoDBClientProviderTest {

    @Mock
    private Configuration config;

    @Test
    public void testProperConfigValuesUsed() {
        new MongoDBClientProvider(config);
        verify(config).getString(eq(ConfigKey.MONGODB_HOST.toString()),
                                 eq(MongoDBClientProvider.DEFAULT_HOST));
        verify(config).getInteger(eq(ConfigKey.MONGODB_PORT.toString()),
                eq(MongoDBClientProvider.DEFAULT_PORT));
    }

    @After
    public void validate() {
        validateMockitoUsage();
    }

}
