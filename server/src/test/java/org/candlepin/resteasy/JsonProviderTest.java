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
package org.candlepin.resteasy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.candlepin.common.config.Configuration;
import org.candlepin.common.jackson.DynamicFilterData;
import org.candlepin.dto.api.v1.ActivationKeyDTO;
import org.candlepin.dto.api.v1.NestedOwnerDTO;
import org.candlepin.jackson.ProductCachedSerializationModule;
import org.candlepin.model.ProductCurator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.ws.rs.core.MediaType;


@ExtendWith(MockitoExtension.class)
public class JsonProviderTest {

    @Mock private Configuration config;
    @Mock private ProductCurator productCurator;
    private ObjectMapper ourMapper;

    @BeforeEach
    public void setUp() throws Exception {
        JsonProvider provider = new JsonProvider(config,
            new ProductCachedSerializationModule(productCurator));
        ourMapper = provider.locateMapper(Object.class, MediaType.APPLICATION_JSON_TYPE);

        ResteasyProviderFactory.clearContextData();
    }

    // This is kind of silly - basically just testing an initial setting...
    @Test
    public void dateFormat() {
        JsonProvider provider = new JsonProvider(config,
            new ProductCachedSerializationModule(productCurator));

        boolean datesAsTimestamps = isEnabled(provider, SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        assertFalse(datesAsTimestamps);
    }

    // This tests to see that the ObjectMapper serializes Date objects to the proper format
    @Test
    public void serializedDateDoesNotIncludeMilliseconds() throws JsonProcessingException {
        Date now = new Date();  // will be initialized to when it was allocated with millisecond precision
        SimpleDateFormat iso8601WithoutMilliseconds = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
        iso8601WithoutMilliseconds.setTimeZone(TimeZone.getTimeZone("UTC"));
        String expectedDate = "\"" + iso8601WithoutMilliseconds.format(now) + "\"";
        String serializedDate = ourMapper.writeValueAsString(now);
        assertTrue(serializedDate.equals(expectedDate));
    }

    /*
     * This tests to see that our DateSerializer does not fail with thread-safety issues caused by
     * SimpleDateFormat. As the https://docs.oracle.com/javase/8/docs/api/java/text/SimpleDateFormat.html doc
     * explains: "Date formats are not synchronized. It is recommended to create separate format instances
     * for each thread. If multiple threads access a format concurrently, it must be synchronized externally."
     *
     * For this reason, SimpleDateFormat has been replaced with DateTimeFormatter, which is thread-safe.
     */
    @Test
    public void canSerializeDatesWithLargeTimestampsConcurrently() {
        final AtomicBoolean processingFailure = new AtomicBoolean(false);
        final AtomicBoolean comparisonFailure = new AtomicBoolean(false);
        ExecutorService ex = Executors.newFixedThreadPool(1000);
        for (int i = 0; i < 5000000; i++) {
            ex.execute(() -> {
                Date randomDate = new Date(getRandomTimestampWithMilliSeconds());

                SimpleDateFormat iso8601WithoutMilliseconds = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
                iso8601WithoutMilliseconds.setTimeZone(TimeZone.getTimeZone("UTC"));
                String expectedDate = "\"" + iso8601WithoutMilliseconds.format(randomDate) + "\"";

                try {
                    String receivedDate = ourMapper.writeValueAsString(randomDate);
                    try {
                        assertEquals(expectedDate, receivedDate, "The date was not serialized properly.");
                    }
                    catch (AssertionError cf) {
                        cf.printStackTrace();
                        comparisonFailure.set(true);
                    }
                }
                catch (JsonProcessingException e) {
                    e.printStackTrace();
                    processingFailure.set(true);
                }
            });
        }

        if (processingFailure.get()) {
            fail("A JsonProcessingException was thrown during concurrent Date serialization.");
        }

        if (comparisonFailure.get()) {
            fail("A date was not serialized properly during concurrent Date serialization.");
        }
    }

    // Generate a random long whose length (13) corresponds to that of a unix timestamp with milliseconds.
    private static long getRandomTimestampWithMilliSeconds() {
        long x = 1000000000000L;
        long y = 9999999999999L;
        Random r = new Random();
        return x + ((long) (r.nextDouble() * (y - x)));
    }

    private boolean isEnabled(JsonProvider provider, SerializationFeature feature) {
        ObjectMapper mapper = provider.locateMapper(Object.class, MediaType.APPLICATION_JSON_TYPE);
        SerializationConfig sConfig = mapper.getSerializationConfig();
        return sConfig.isEnabled(feature);
    }

    @Test
    public void testDynamicPropertyFilterExcludeSingleProperty() {
        DynamicFilterData filterData = new DynamicFilterData();
        filterData.excludeAttribute("name");
        ResteasyProviderFactory.pushContext(DynamicFilterData.class, filterData);

        ActivationKeyDTO keyDTO = new ActivationKeyDTO();
        String serializedKey = "";
        try {
            serializedKey = ourMapper.writeValueAsString(keyDTO);
        }
        catch (JsonProcessingException e) {
            fail("Serializing ActivationKeyDTO failed!");
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode akNode = null;
        try {
            akNode = mapper.readTree(serializedKey);
        }
        catch (JsonProcessingException e) {
            fail("Parsing serialized ActivationKeyDTO failed!");
        }

        assertTrue(akNode.has("id"), "The 'id' field should NOT have been excluded!");
        assertTrue(akNode.has("description"), "The 'description' field should NOT have been excluded!");
        assertTrue(akNode.has("releaseVer"), "The 'releaseVer' field should NOT have been excluded!");
        assertFalse(akNode.has("name"), "The 'name' field should have been excluded!");
    }

    @Test
    public void testDynamicPropertyFilterExcludeMultipleProperties() {
        DynamicFilterData filterData = new DynamicFilterData();
        filterData.excludeAttribute("name");
        filterData.excludeAttribute("addOns");
        filterData.excludeAttribute("serviceLevel");
        ResteasyProviderFactory.pushContext(DynamicFilterData.class, filterData);

        ActivationKeyDTO keyDTO = new ActivationKeyDTO();
        String serializedKey = "";
        try {
            serializedKey = ourMapper.writeValueAsString(keyDTO);
        }
        catch (JsonProcessingException e) {
            fail("Serializing ActivationKeyDTO failed!");
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode akNode = null;
        try {
            akNode = mapper.readTree(serializedKey);
        }
        catch (JsonProcessingException e) {
            fail("Parsing serialized ActivationKeyDTO failed!");
        }

        assertTrue(akNode.has("id"), "The 'id' field should NOT have been excluded!");
        assertTrue(akNode.has("description"), "The 'description' field should NOT have been excluded!");
        assertTrue(akNode.has("releaseVer"), "The 'releaseVer' field should NOT have been excluded!");
        assertFalse(akNode.has("name"), "The 'name' field should have been excluded!");
        assertFalse(akNode.has("addOns"), "The 'addOns' field should have been excluded!");
        assertFalse(akNode.has("serviceLevel"), "The 'serviceLevel' field should have been excluded!");
    }

    @Test
    public void testDynamicPropertyFilterIncludeSingleProperty() {
        DynamicFilterData filterData = new DynamicFilterData();
        filterData.includeAttribute("name");
        filterData.setWhitelistMode(true); // When only includes are set, we should be in whitelist mode
        ResteasyProviderFactory.pushContext(DynamicFilterData.class, filterData);

        ActivationKeyDTO keyDTO = new ActivationKeyDTO();
        String serializedKey = "";
        try {
            serializedKey = ourMapper.writeValueAsString(keyDTO);
        }
        catch (JsonProcessingException e) {
            fail("Serializing ActivationKeyDTO failed!");
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode akNode = null;
        try {
            akNode = mapper.readTree(serializedKey);
        }
        catch (JsonProcessingException e) {
            fail("Parsing serialized ActivationKeyDTO failed!");
        }
        assertEquals(1, akNode.size());
        assertTrue(akNode.has("name"), "The 'name' field should have been included!");
    }

    @Test
    public void testDynamicPropertyFilterIncludeMultipleProperties() {
        DynamicFilterData filterData = new DynamicFilterData();
        filterData.includeAttribute("name");
        filterData.includeAttribute("releaseVer");
        filterData.includeAttribute("addOns");
        filterData.setWhitelistMode(true); // When only includes are set, we should be in whitelist mode
        ResteasyProviderFactory.pushContext(DynamicFilterData.class, filterData);

        ActivationKeyDTO keyDTO = new ActivationKeyDTO();
        String serializedKey = "";
        try {
            serializedKey = ourMapper.writeValueAsString(keyDTO);
        }
        catch (JsonProcessingException e) {
            fail("Serializing ActivationKeyDTO failed!");
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode akNode = null;
        try {
            akNode = mapper.readTree(serializedKey);
        }
        catch (JsonProcessingException e) {
            fail("Parsing serialized ActivationKeyDTO failed!");
        }
        assertEquals(3, akNode.size());
        assertTrue(akNode.has("name"), "The 'name' field should have been included!");
        assertTrue(akNode.has("releaseVer"), "The 'releaseVer' field should have been included!");
        assertTrue(akNode.has("addOns"), "The 'addOns' field should have been included!");
    }

    @Test
    public void testDynamicPropertyFilterIncludeNestedProperty() {
        DynamicFilterData filterData = new DynamicFilterData();
        filterData.includeAttribute("owner.id");
        filterData.setWhitelistMode(true); // When only includes are set, we should be in whitelist mode
        ResteasyProviderFactory.pushContext(DynamicFilterData.class, filterData);

        ActivationKeyDTO keyDTO = new ActivationKeyDTO();
        NestedOwnerDTO ownerDTO = new NestedOwnerDTO()
            .key("owner_key")
            .id("owner_id");
        keyDTO.setOwner(ownerDTO);

        String serializedKey = "";
        try {
            serializedKey = ourMapper.writeValueAsString(keyDTO);
        }
        catch (JsonProcessingException e) {
            fail("Serializing ActivationKeyDTO failed!");
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode akNode = null;
        try {
            akNode = mapper.readTree(serializedKey);
        }
        catch (JsonProcessingException e) {
            fail("Parsing serialized ActivationKeyDTO failed!");
        }
        assertEquals(1, akNode.size());
        assertTrue(akNode.has("owner"), "The 'owner' field should have been included!");
        assertEquals(1, akNode.get("owner").size());
        assertTrue(akNode.get("owner").has("id"), "The 'owner.id' field should have been included!");
    }

    @Test
    public void testDynamicPropertyFilterIncludeNestedPropertiesOnListElements() {
        DynamicFilterData filterData = new DynamicFilterData();
        filterData.includeAttribute("owner.id");
        filterData.setWhitelistMode(true); // When only includes are set, we should be in whitelist mode
        ResteasyProviderFactory.pushContext(DynamicFilterData.class, filterData);

        NestedOwnerDTO ownerDTO = new NestedOwnerDTO()
            .key("owner_key")
            .id("owner_id");
        ActivationKeyDTO keyDTO1 = new ActivationKeyDTO();
        keyDTO1.setOwner(ownerDTO);
        ActivationKeyDTO keyDTO2 = new ActivationKeyDTO();
        keyDTO2.setOwner(ownerDTO);
        List<ActivationKeyDTO> activationKeys = new ArrayList<>();
        activationKeys.add(keyDTO1);
        activationKeys.add(keyDTO2);

        String serializedKeys = "";
        try {
            serializedKeys = ourMapper.writeValueAsString(activationKeys);
            System.out.println(serializedKeys);
        }
        catch (JsonProcessingException e) {
            fail("Serializing a list ActivationKeyDTOs failed!");
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode akNode = null;
        try {
            akNode = mapper.readTree(serializedKeys);
        }
        catch (JsonProcessingException e) {
            fail("Parsing serialized ActivationKeyDTO list failed!");
        }
        assertEquals(2, akNode.size());
        assertTrue(akNode.get(0).has("owner"), "The 'owner' field should have been included!");
        assertEquals(1, akNode.get(0).get("owner").size());
        assertTrue(akNode.get(0).get("owner").has("id"), "The 'owner.id' field should have been included!");
        assertTrue(akNode.get(1).has("owner"), "The 'owner' field should have been included!");
        assertEquals(1, akNode.get(1).get("owner").size());
        assertTrue(akNode.get(1).get("owner").has("id"), "The 'owner.id' field should have been included!");
    }
}
