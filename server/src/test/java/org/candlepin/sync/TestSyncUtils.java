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
package org.candlepin.sync;

import org.candlepin.common.config.Configuration;
import org.candlepin.jackson.ProductCachedSerializationModule;
import org.candlepin.model.ProductCurator;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TestSyncUtils {

    private TestSyncUtils() {

    }

    public static ObjectMapper getTestSyncUtils(Configuration config) {
        ProductCurator mockProductCurator = Mockito.mock(ProductCurator.class);
        ProductCachedSerializationModule productCachedModule = new ProductCachedSerializationModule(
            mockProductCurator);
        return new SyncUtils(config, productCachedModule).getObjectMapper();
    }
}
