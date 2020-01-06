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

import org.candlepin.common.config.MapConfiguration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.test.TestUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;

import static org.junit.Assert.assertTrue;

/**
 * ConsumerTypeExporterTest
 */
@RunWith(MockitoJUnitRunner.class)
public class ConsumerTypeExporterTest {

    @Mock private ConsumerTypeCurator mockConsumerTypeCurator;
    @Mock private EnvironmentCurator mockEnvironmentCurator;
    @Mock private OwnerCurator ownerCurator;
    @Mock private EntitlementCurator entitlementCurator;
    private ModelTranslator translator;

    @Test
    public void testConsumerTypeExport() throws IOException {
        ObjectMapper mapper = TestSyncUtils.getTestSyncUtils(new MapConfiguration(
            new HashMap<String, String>() {
                {
                    put(ConfigProperties.FAIL_ON_UNKNOWN_IMPORT_PROPERTIES, "false");
                }
            }
        ));

        translator = new StandardTranslator(mockConsumerTypeCurator, mockEnvironmentCurator, ownerCurator,
            entitlementCurator);
        ConsumerTypeExporter consumerType = new ConsumerTypeExporter(translator);

        StringWriter writer = new StringWriter();

        ConsumerType type = new ConsumerType("TESTTYPE");

        consumerType.export(mapper, writer, type);

        StringBuffer json = new StringBuffer();
        json.append("{\"id\":null,\"label\":\"TESTTYPE\",");
        json.append("\"manifest\":false}");
        assertTrue(TestUtil.isJsonEqual(json.toString(), writer.toString()));
    }

}
