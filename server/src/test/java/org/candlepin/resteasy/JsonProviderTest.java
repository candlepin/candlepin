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
import org.candlepin.model.ProductCurator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.junit.ComparisonFailure;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.ws.rs.core.MediaType;



@RunWith(MockitoJUnitRunner.class)
public class JsonProviderTest {

    @Mock private Configuration config;
    @Mock private ProductCurator productCurator;

    // This is kind of silly - basically just testing an initial setting...
    @Test
    public void dateFormat() {
        JsonProvider provider = new JsonProvider(config);

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
        JsonProvider provider = new JsonProvider(config);
        ObjectMapper mapper = provider.locateMapper(Object.class, MediaType.APPLICATION_JSON_TYPE);
        String serializedDate = mapper.writeValueAsString(now);
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
        JsonProvider provider = new JsonProvider(config);
        ObjectMapper mapper = provider.locateMapper(Object.class, MediaType.APPLICATION_JSON_TYPE);

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
                    String receivedDate = mapper.writeValueAsString(randomDate);
                    try {
                        assertEquals("The date was not serialized properly.", expectedDate, receivedDate);
                    }
                    catch (ComparisonFailure cf) {
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

}
