/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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

package org.candlepin.resource.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import org.candlepin.dto.api.server.v1.ConsumerDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCloudData;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xnap.commons.i18n.I18n;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class ConsumerCloudDataBuilderTest {

    @Mock
    private I18n i18n;
    @Mock
    private CloudProviderFactParser supportedParser;
    @Mock
    private CloudProviderFactParser unsupportedParser;

    @Test
    void testBuildNoSupportedParser() {
        Map<String, String> facts = Map.of("key", "value");
        Consumer consumer = new Consumer()
            .setFacts(facts);

        when(unsupportedParser.isSupported(facts)).thenReturn(false);

        Set<CloudProviderFactParser> parsers = Set.of(unsupportedParser);
        ConsumerCloudDataBuilder builder = new ConsumerCloudDataBuilder(i18n, parsers);

        Optional<ConsumerCloudData> result = builder.build(consumer);
        assertTrue(result.isEmpty());
    }

    @Test
    void testBuildSingleSupportedParser() {
        Map<String, String> facts = Map.of("key", "value");
        Consumer consumer = new Consumer()
            .setFacts(facts);

        when(supportedParser.isSupported(facts)).thenReturn(true);
        when(supportedParser.getAccountId(facts)).thenReturn(Optional.of("account123"));
        when(supportedParser.getOfferingIds(facts))
            .thenReturn(Optional.of(List.of("offering1", "offering2")));
        when(supportedParser.getInstanceId(facts)).thenReturn(Optional.of("instance456"));
        when(supportedParser.getShortName()).thenReturn("aws");
        when(unsupportedParser.isSupported(facts)).thenReturn(false);

        Set<CloudProviderFactParser> parsers = Set.of(supportedParser, unsupportedParser);
        ConsumerCloudDataBuilder builder = new ConsumerCloudDataBuilder(i18n, parsers);

        Optional<ConsumerCloudData> result = builder.build(consumer);
        assertTrue(result.isPresent());

        ConsumerCloudData data = result.get();
        assertEquals("account123", data.getCloudAccountId());
        assertEquals("instance456", data.getCloudInstanceId());
        assertEquals(List.of("offering1", "offering2"), data.getCloudOfferingIds());
        assertEquals("aws", data.getCloudProviderShortName());
    }

    @Test
    void testBuildMultipleSupportedParsers() {
        Map<String, String> facts = Map.of("key", "value");
        Consumer consumer = new Consumer()
            .setFacts(facts);
        CloudProviderFactParser anotherSupportedParser = mock(CloudProviderFactParser.class);

        when(supportedParser.isSupported(facts)).thenReturn(true);
        when(anotherSupportedParser.isSupported(facts)).thenReturn(true);

        Set<CloudProviderFactParser> parsers = Set.of(supportedParser, anotherSupportedParser);
        ConsumerCloudDataBuilder builder = new ConsumerCloudDataBuilder(i18n, parsers);

        assertThrows(BadRequestException.class, () -> builder.build(consumer));
    }

    @Test
    void testBuildConsumerDtoNoSupportedParser() {
        Map<String, String> facts = Map.of("key", "value");
        ConsumerDTO consumerDTO = new ConsumerDTO();
        consumerDTO.setFacts(facts);

        when(unsupportedParser.isSupported(facts)).thenReturn(false);

        Set<CloudProviderFactParser> parsers = Set.of(unsupportedParser);
        ConsumerCloudDataBuilder builder = new ConsumerCloudDataBuilder(i18n, parsers);

        Optional<ConsumerCloudData> result = builder.build(consumerDTO);
        assertTrue(result.isEmpty());
    }

    @Test
    void testBuildConsumerDtoSingleSupportedParser() {
        ConsumerDTO consumerDTO = new ConsumerDTO();
        Map<String, String> facts = Map.of("key", "value");
        consumerDTO.setFacts(facts);

        when(supportedParser.isSupported(facts)).thenReturn(true);
        when(supportedParser.getAccountId(facts)).thenReturn(Optional.of("accountXYZ"));
        when(supportedParser.getOfferingIds(facts)).thenReturn(Optional.of(List.of("offerA")));
        when(supportedParser.getInstanceId(facts)).thenReturn(Optional.of("instanceABC"));
        when(supportedParser.getShortName()).thenReturn("gcp");

        Set<CloudProviderFactParser> parsers = Set.of(supportedParser);
        ConsumerCloudDataBuilder builder = new ConsumerCloudDataBuilder(i18n, parsers);

        Optional<ConsumerCloudData> result = builder.build(consumerDTO);
        assertTrue(result.isPresent());

        ConsumerCloudData data = result.get();
        assertEquals("accountXYZ", data.getCloudAccountId());
        assertEquals("instanceABC", data.getCloudInstanceId());
        assertEquals(List.of("offerA"), data.getCloudOfferingIds());
        assertEquals("gcp", data.getCloudProviderShortName());
    }
}

