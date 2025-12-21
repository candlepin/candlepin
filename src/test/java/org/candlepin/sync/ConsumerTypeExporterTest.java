/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ObjectMapperFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;


@ExtendWith(MockitoExtension.class)
public class ConsumerTypeExporterTest {

    @Mock
    private ConsumerTypeCurator mockConsumerTypeCurator;
    @Mock
    private EnvironmentCurator mockEnvironmentCurator;
    @Mock
    private OwnerCurator ownerCurator;
    private ModelTranslator translator;

    @Test
    public void testConsumerTypeExport() throws IOException {
        DevConfig config = TestConfig.custom(Map.of(
            ConfigProperties.FAIL_ON_UNKNOWN_IMPORT_PROPERTIES, "false"));

        ObjectMapper mapper = ObjectMapperFactory.getSyncObjectMapper(config);

        translator = new StandardTranslator(mockConsumerTypeCurator, mockEnvironmentCurator, ownerCurator);
        ConsumerTypeExporter consumerType = new ConsumerTypeExporter(translator);

        StringWriter writer = new StringWriter();

        ConsumerType type = new ConsumerType("TESTTYPE");

        consumerType.export(mapper, writer, type);

        String json = "{\"id\":null,\"label\":\"TESTTYPE\",\"manifest\":false}";
        assertTrue(TestUtil.isJsonEqual(json, writer.toString()));
    }

}
