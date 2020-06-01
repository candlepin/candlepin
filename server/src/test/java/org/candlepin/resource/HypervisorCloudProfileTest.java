/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.auth.permissions.PermissionFactory;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.dto.api.v1.GuestIdDTO;
import org.candlepin.dto.api.v1.HypervisorConsumerDTO;
import org.candlepin.dto.api.v1.HypervisorUpdateResultDTO;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.HypervisorId;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Role;
import org.candlepin.model.User;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

/**
 * Test suite for the HypervisorResource and
 * GuestIdResource update methods. This validates the
 * Subscription profile time-stamp updates
 */
public class HypervisorCloudProfileTest extends DatabaseTestFixture {

    private static final String USER_NAME = "testing user";

    @Inject private ConsumerCurator consumerCurator;
    @Inject private ConsumerTypeCurator consumerTypeCurator;
    @Inject private GuestIdResource guestIdResource;
    @Inject private OwnerCurator ownerCurator;
    @Inject private EnvironmentCurator environmentCurator;
    @Inject private PermissionFactory permFactory;
    @Inject private HypervisorResource hypervisorResource;

    private ConsumerType hypervisorType;

    private UserPrincipal principal;
    private Consumer consumer;
    private Owner owner;
    private Role ownerAdminRole;
    private User someuser;

    @BeforeEach
    public void setUp() {

        this.modelTranslator = new StandardTranslator(this.consumerTypeCurator, this.environmentCurator,
            this.ownerCurator);

        owner = ownerCurator.create(new Owner("test-owner"));

        someuser = userCurator.create(new User(USER_NAME, "dontcare"));
        ownerAdminRole = createAdminRole(owner);
        ownerAdminRole.addUser(someuser);
        roleCurator.create(ownerAdminRole);

        Collection<Permission> perms = permFactory.createPermissions(someuser,
            ownerAdminRole.getPermissions());

        principal = new UserPrincipal(USER_NAME, perms, false);
        setupPrincipal(principal);

        hypervisorType = consumerTypeCurator.getByLabel(ConsumerType.ConsumerTypeEnum.HYPERVISOR.getLabel());

        consumer = TestUtil.createConsumer(hypervisorType, owner);
        consumer = consumerCurator.create(consumer);
    }

    @Test
    public void testCloudProfileUpdatedOnGuestIdsForHostConsumerUpdates() {
        Map<String, List<String>> hostGuestMap = new HashMap<>();
        String hypervisorId = "test-host";
        hostGuestMap.put(hypervisorId, new ArrayList<>(Collections
            .singletonList("GUEST_B")));

        HypervisorUpdateResultDTO result = hypervisorResource.hypervisorUpdate(
            owner.getKey(), hostGuestMap, true);

        List<HypervisorConsumerDTO> created = new ArrayList<>(result.getCreated());

        assertEquals(1, created.size());
        assertNotNull(consumerCurator.findByUuid(created.get(0).getUuid()).getRHCloudProfileModified());

        hostGuestMap.put(hypervisorId, new ArrayList<>(Collections
            .singletonList("GUEST_C")));

        result = hypervisorResource.hypervisorUpdate(
            owner.getKey(), hostGuestMap, true);

        List<HypervisorConsumerDTO> updated = new ArrayList<>(result.getUpdated());

        assertEquals(1, updated.size());
        assertNotNull(consumerCurator.findByUuid(updated.get(0).getUuid()).getRHCloudProfileModified());
    }

    @Test
    public void testCloudProfileUpdatedOnConsumerTypeUpdates() {
        ConsumerType system = new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM);
        consumerTypeCurator.create(system);

        String hypervisorid = "test-host";
        Consumer testConsumer = new Consumer(hypervisorid, someuser.getUsername(), owner, system);
        HypervisorId hypervisorId = new HypervisorId(hypervisorid);
        hypervisorId.setOwner(owner);
        testConsumer.setHypervisorId(hypervisorId);
        testConsumer = consumerCurator.create(testConsumer);

        Map<String, List<String>> hostGuestMap = new HashMap<>();
        hostGuestMap.put(testConsumer.getName(), new ArrayList<>());

        HypervisorUpdateResultDTO result = hypervisorResource.hypervisorUpdate(
            owner.getKey(), hostGuestMap, true);

        List<HypervisorConsumerDTO> updated = new ArrayList<>(result.getUpdated());

        assertEquals(1, updated.size());
        assertNotNull(consumerCurator.findByUuid(updated.get(0).getUuid()).getRHCloudProfileModified());
    }

    @Test
    public void testCloudProfileUpdatedOnGuestUpdate() {
        Consumer testConsumer = new Consumer("test_consumer", someuser.getUsername(), owner, hypervisorType);
        testConsumer = consumerCurator.create(testConsumer);

        GuestIdDTO guest = TestUtil.createGuestIdDTO("some_guest");
        GuestId guestEnt = new GuestId();
        guestEnt.setId("some_id");

        Date beforeUpdateTimestamp = consumerCurator.findByUuid(testConsumer.getUuid())
            .getRHCloudProfileModified();

        guestIdResource.updateGuest(testConsumer.getUuid(), guest.getGuestId(), guest);
        Date afterUpdateTimestamp = consumerCurator.findByUuid(testConsumer.getUuid())
            .getRHCloudProfileModified();

        assertNotNull(afterUpdateTimestamp);
        assertNotEquals(beforeUpdateTimestamp, afterUpdateTimestamp);
    }

    @Test
    public void testCloudProfileUpdatedOnMultipleGuestUpdate() {
        List<GuestIdDTO> guestIds = new LinkedList<>();
        guestIds.add(TestUtil.createGuestIdDTO("1"));

        Date beforeUpdateTimestamp = consumerCurator.findByUuid(consumer.getUuid())
            .getRHCloudProfileModified();

        guestIdResource.updateGuests(consumer.getUuid(), guestIds);
        Date afterUpdateTimestamp = consumerCurator.findByUuid(consumer.getUuid())
            .getRHCloudProfileModified();

        assertNotNull(afterUpdateTimestamp);
        assertNotEquals(beforeUpdateTimestamp, afterUpdateTimestamp);
    }

    @Test
    public void testCloudProfileNotUpdatedOnSameGuestUpdate() {
        List<GuestIdDTO> guestIds = new LinkedList<>();
        guestIds.add(TestUtil.createGuestIdDTO("1"));

        Date beforeUpdateTimestamp = consumerCurator.findByUuid(consumer.getUuid())
            .getRHCloudProfileModified();

        guestIdResource.updateGuests(consumer.getUuid(), guestIds);
        Date afterUpdateTimestamp = consumerCurator.findByUuid(consumer.getUuid())
            .getRHCloudProfileModified();

        assertNotEquals(beforeUpdateTimestamp, afterUpdateTimestamp);

        //Updating the same Guests
        beforeUpdateTimestamp = consumerCurator.findByUuid(consumer.getUuid())
            .getRHCloudProfileModified();

        guestIdResource.updateGuests(consumer.getUuid(), guestIds);
        afterUpdateTimestamp = consumerCurator.findByUuid(consumer.getUuid())
            .getRHCloudProfileModified();

        assertEquals(beforeUpdateTimestamp, afterUpdateTimestamp);
    }

    @Test
    public void testCloudProfileNotUpdatedWithNoUpdatesForHypervisor() {
        Map<String, List<String>> hostGuestMap = new HashMap<>();
        String hypervisorId = "test-host";
        hostGuestMap.put(hypervisorId, new ArrayList<>(Collections
            .singletonList("GUEST_B")));

        HypervisorUpdateResultDTO result = hypervisorResource.hypervisorUpdate(
            owner.getKey(), hostGuestMap, true);

        List<HypervisorConsumerDTO> created = new ArrayList<>(result.getCreated());
        Date profileCreated = consumerCurator.findByUuid(created.get(0).getUuid())
            .getRHCloudProfileModified();

        assertEquals(1, created.size());
        assertNotNull(profileCreated);

        result = hypervisorResource.hypervisorUpdate(
            owner.getKey(), hostGuestMap, true);

        List<HypervisorConsumerDTO> unChanged = new ArrayList<>(result.getUnchanged());
        Date profileModified = consumerCurator.findByUuid(unChanged.get(0).getUuid())
            .getRHCloudProfileModified();
        assertEquals(1, unChanged.size());

        assertEquals(profileCreated, profileModified);
    }

}
