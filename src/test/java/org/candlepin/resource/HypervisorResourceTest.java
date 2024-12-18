/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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
package org.candlepin.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;

import org.candlepin.async.JobManager;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.dto.api.server.v1.HypervisorConsumerWithGuestDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.HypervisorConsumerWithGuest;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.resource.util.GuestMigration;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.inject.Provider;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class HypervisorResourceTest extends DatabaseTestFixture {

    private static final int MAX_CONSUMER_UUIDS = 10;

    @Mock
    private ConsumerResource consumerResource;
    @Mock
    private ConsumerCurator consumerCurator;
    @Mock
    private ConsumerTypeCurator consumerTypeCurator;
    @Mock
    private I18n i18n;
    @Mock
    private OwnerCurator ownerCurator;
    @Mock
    private Provider<GuestMigration> migrationProvider;
    @Mock
    private JobManager jobManager;
    @Mock
    private PrincipalProvider principalProvider;
    @Mock
    private ObjectMapper mapper;
    @Mock
    private Configuration config;

    private HypervisorResource hypervisorResource;

    private Owner owner;
    private String ownerKey;

    @BeforeEach
    private void beforeEach() {
        hypervisorResource = new HypervisorResource(this.consumerResource,
            this.consumerCurator,
            this.consumerTypeCurator,
            this.i18n,
            this.ownerCurator,
            this.migrationProvider,
            this.modelTranslator,
            this.jobManager,
            this.principalProvider,
            this.mapper,
            this.config);
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testGetHypervisorsAndGuestsWithInvalidOwnerKey(String invalidKey) {
        assertThrows(BadRequestException.class, () -> {
            hypervisorResource.getHypervisorsAndGuests(invalidKey, List.of("uuid"));
        });
    }

    @Test
    public void testGetHypervisorsAndGuestsWithExceedingTheRetrievalLimit() {
        doReturn(MAX_CONSUMER_UUIDS).when(config)
            .getInt(ConfigProperties.HYPERVISORS_AND_GUEST_RETRIEVAL_LIMIT);

        List<String> consumerUuids = new ArrayList<>();
        for (int i = 0; i < MAX_CONSUMER_UUIDS + 1; i++) {
            consumerUuids.add(TestUtil.randomString());
        }

        assertThrows(BadRequestException.class, () -> {
            hypervisorResource.getHypervisorsAndGuests("owner-key", consumerUuids);
        });
    }

    @Test
    public void testGetHypervisorsAndGuests() {
        doReturn(MAX_CONSUMER_UUIDS).when(config)
            .getInt(ConfigProperties.HYPERVISORS_AND_GUEST_RETRIEVAL_LIMIT);

        ownerKey = TestUtil.randomString("owner-key-");
        owner = new Owner()
            .setId(TestUtil.randomString("id-"))
            .setKey(ownerKey);

        doReturn(owner).when(ownerCurator).getByKey(ownerKey);

        List<String> consumerUuids = List.of("consumer-uuid-1", "consumer-uuid-2");

        String h1GuestId = TestUtil.randomString("host-1-guest-id-");
        String h1GuestUuid = TestUtil.randomString("host-1-guest-uuid-");
        String h1HostName = TestUtil.randomString("host-1-host-name-");
        String h1HostUuid = TestUtil.randomString("host-1-host-uuid-");
        HypervisorConsumerWithGuest hostGuest1 = new HypervisorConsumerWithGuest(h1HostUuid, h1HostName,
            h1GuestUuid, h1GuestId);

        String h2GuestId = TestUtil.randomString("host-2-guest-id-");
        String h2GuestUuid = TestUtil.randomString("host-2-guest-uuid-");
        String h2HostName = TestUtil.randomString("host-2-host-name-");
        String h2HostUuid = TestUtil.randomString("host-2-host-uuid-");
        HypervisorConsumerWithGuest hostGuest2 = new HypervisorConsumerWithGuest(h2HostUuid, h2HostName,
            h2GuestUuid, h2GuestId);

        doReturn(List.of(hostGuest1, hostGuest2)).when(consumerCurator)
            .getHypervisorConsumersWithGuests(consumerUuids, owner.getOwnerId());

        Stream<HypervisorConsumerWithGuestDTO> actual = hypervisorResource
            .getHypervisorsAndGuests(ownerKey, consumerUuids);

        List<HypervisorConsumerWithGuestDTO> expected = List.of(hostGuest1, hostGuest2)
            .stream()
            .map(this.modelTranslator.getStreamMapper(HypervisorConsumerWithGuest.class,
                HypervisorConsumerWithGuestDTO.class))
            .toList();

        assertThat(actual)
            .isNotNull()
            .containsExactlyInAnyOrderElementsOf(expected);
    }

}
