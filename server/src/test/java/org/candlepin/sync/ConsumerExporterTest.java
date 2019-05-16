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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.candlepin.common.config.MapConfiguration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
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

/**
 * ConsumerExporterTest
 */
@RunWith(MockitoJUnitRunner.class)
public class ConsumerExporterTest {

    @Mock private ConsumerTypeCurator mockConsumerTypeCurator;
    @Mock private EnvironmentCurator mockEnvironmentCurator;
    @Mock private OwnerCurator ownerCurator;
    private ModelTranslator translator;

    @Test
    public void testConsumerExport() throws IOException {
        ObjectMapper mapper = TestSyncUtils
            .getTestSyncUtils(new MapConfiguration(new HashMap<String, String>() {
                {
                    put(ConfigProperties.FAIL_ON_UNKNOWN_IMPORT_PROPERTIES, "false");
                }
            }));

        translator = new StandardTranslator(mockConsumerTypeCurator, mockEnvironmentCurator, ownerCurator);
        ConsumerExporter exporter = new ConsumerExporter(translator);
        ConsumerType ctype = new ConsumerType("candlepin");
        ctype.setId("8888");
        ctype.setManifest(true);

        StringWriter writer = new StringWriter();

        Consumer consumer = new Consumer();
        consumer.setUuid("test-uuid");
        consumer.setName("testy consumer");
        consumer.setType(ctype);
        consumer.setContentAccessMode("access_mode");

        when(mockConsumerTypeCurator.getConsumerType(eq(consumer))).thenReturn(ctype);
        when(mockConsumerTypeCurator.get(eq(ctype.getId()))).thenReturn(ctype);

        exporter.export(mapper, writer, consumer, "/subscriptions", "/candlepin");

        StringBuffer json = new StringBuffer();
        json.append("{\"uuid\":\"").append(consumer.getUuid()).append("\",");
        json.append("\"name\":\"").append(consumer.getName()).append("\",");
        json.append("\"type\":");
        json.append("{\"id\":\"").append(ctype.getId()).append("\",");
        json.append("\"label\":\"").append(ctype.getLabel()).append("\",");
        json.append("\"manifest\":").append(ctype.isManifest()).append("},");
        json.append("\"owner\":null,");
        json.append("\"urlWeb\":\"/subscriptions\",");
        json.append("\"urlApi\":\"/candlepin\",");
        json.append("\"contentAccessMode\":\"access_mode\"}");
        assertTrue(json.toString() + "\n" + writer.toString(),
            TestUtil.isJsonEqual(json.toString(), writer.toString()));

        // change sibling order to ensure that isJsonEqual can reconcile
        json = new StringBuffer();
        json.append("{\"uuid\":\"").append(consumer.getUuid()).append("\",");
        json.append("\"type\":");
        json.append("{\"id\":\"").append(ctype.getId()).append("\",");
        json.append("\"label\":\"").append(ctype.getLabel()).append("\",");
        json.append("\"manifest\":").append(ctype.isManifest()).append("},");
        json.append("\"owner\":null,");
        json.append("\"name\":\"").append(consumer.getName()).append("\",");
        json.append("\"urlApi\":\"/candlepin\",");
        json.append("\"urlWeb\":\"/subscriptions\",");
        json.append("\"contentAccessMode\":\"access_mode\"}");
        assertTrue(TestUtil.isJsonEqual(json.toString(), writer.toString()));
    }
}
