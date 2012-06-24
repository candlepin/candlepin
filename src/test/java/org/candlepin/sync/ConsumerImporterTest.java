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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

/**
 * ConsumerImporterTest
 */
public class ConsumerImporterTest {

    private ConsumerImporter importer;
    private ObjectMapper mapper;
    private OwnerCurator curator;
    private I18n i18n;

    @Before
    public void setUp() {
        curator = mock(OwnerCurator.class);
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        importer = new ConsumerImporter(curator, i18n);
        mapper = SyncUtils.getObjectMapper(new Config(new HashMap<String, String>()));
    }

    @Test
    public void importShouldCreateAValidConsumer() throws IOException, ImporterException {
        ConsumerDto consumer =
            importer.createObject(mapper, new StringReader("{\"uuid\":\"test-uuid\",\"name\":\"test-name\"}"));

        assertEquals("test-uuid", consumer.getUuid());
        assertEquals("test-name", consumer.getName());
    }

    @Test
    public void importHandlesUnknownPropertiesGracefully() throws Exception {

        // Override default config to error out on unknown properties:
        Map<String, String> configProps = new HashMap<String, String>();
        configProps.put(ConfigProperties.FAIL_ON_UNKNOWN_IMPORT_PROPERTIES, "false");
        mapper = SyncUtils.getObjectMapper(new Config(configProps));

        ConsumerDto consumer =
            importer.createObject(mapper, new StringReader(
                "{\"uuid\":\"test-uuid\", \"unknown\":\"notreal\"}"));
        assertEquals("test-uuid", consumer.getUuid());
    }

    @Test(expected = JsonMappingException.class)
    public void importFailsOnUnknownPropertiesWithNonDefaultConfig() throws Exception {
        // Override default config to error out on unknown properties:
        Map<String, String> configProps = new HashMap<String, String>();
        configProps.put(ConfigProperties.FAIL_ON_UNKNOWN_IMPORT_PROPERTIES, "true");
        mapper = SyncUtils.getObjectMapper(new Config(configProps));

        importer.createObject(mapper, new StringReader(
            "{\"uuid\":\"test-uuid\", \"unknown\":\"notreal\"}"));
    }

    @Test
    public void importConsumerWithNullUuidOnOwnerShouldSetUuid() throws IOException, ImporterException {
        Owner owner = new Owner();
        ConsumerDto consumer = new ConsumerDto();
        consumer.setUuid("test-uuid");

        importer.store(owner, consumer);
        assertEquals("test-uuid", owner.getUpstreamUuid());
        verify(curator).merge(owner);
    }

    @Test
    public void importConsumerWithSameUuidOnOwnerShouldDoNothing() throws ImporterException {
        Owner owner = new Owner();
        owner.setUpstreamUuid("test-uuid");
        ConsumerDto consumer = new ConsumerDto();
        consumer.setUuid("test-uuid");

        importer.store(owner, consumer);

        assertEquals("test-uuid", owner.getUpstreamUuid());
    }

    @Test(expected = SyncDataFormatException.class)
    public void importConsumerWithSameUuidOnAnotherOwnerShouldThrowException()
        throws ImporterException {
        Owner owner = new Owner();
        String upstreamUuid = "test-uuid";
        owner.setUpstreamUuid(upstreamUuid);
        ConsumerDto consumer = new ConsumerDto();
        consumer.setUuid("test-uuid");

        Owner anotherOwner = new Owner("other", "Other");
        anotherOwner.setId("blah");
        anotherOwner.setUpstreamUuid(upstreamUuid);
        when(curator.lookupWithUpstreamUuid(consumer.getUuid())).thenReturn(anotherOwner);

        importer.store(owner, consumer);
    }

    @Test(expected = ImporterException.class)
    public void importConsumerWithMismatchedUuidShouldThrowException() throws ImporterException {
        Owner owner = new Owner();
        owner.setUpstreamUuid("another-test-uuid");
        ConsumerDto consumer = new ConsumerDto();
        consumer.setUuid("test-uuid");

        importer.store(owner, consumer);
    }

    @Test(expected = ImporterException.class)
    public void importConsumerWithNullUuidOnConsumerShouldThrowException() throws ImporterException {
        Owner owner = new Owner();
        ConsumerDto consumer = new ConsumerDto();
        consumer.setUuid(null);

        importer.store(owner, consumer);
    }
}
