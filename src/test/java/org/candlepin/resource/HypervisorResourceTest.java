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
package org.candlepin.resource;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.Access;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.model.ActivationKeyCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.resource.dto.HypervisorCheckInResult;
import org.candlepin.service.IdentityCertServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.UserServiceAdapter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public class HypervisorResourceTest {

    @Mock
    private UserServiceAdapter userService;

    @Mock
    private IdentityCertServiceAdapter idCertService;

    @Mock
    private SubscriptionServiceAdapter subscriptionService;

    @Mock
    private ConsumerCurator consumerCurator;

    @Mock
    private ConsumerTypeCurator consumerTypeCurator;

    @Mock
    private OwnerCurator ownerCurator;

    @Mock
    private EventSink sink;

    @Mock
    private EventFactory eventFactory;

    @Mock
    private ActivationKeyCurator activationKeyCurator;

    @Mock
    private UserPrincipal principal;

    @Mock
    private ComplianceRules complianceRules;

    @Mock
    private DeletedConsumerCurator deletedConsumerCurator;

    private ConsumerResource consumerResource;

    private I18n i18n;

    private ConsumerType hypervisorType;

    private HypervisorResource hypervisorResource;

    @Before
    public void setupTest() {
        this.i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        this.hypervisorType = new ConsumerType(ConsumerTypeEnum.HYPERVISOR);
        this.consumerResource = new ConsumerResource(this.consumerCurator,
            this.consumerTypeCurator, null, this.subscriptionService, null,
            this.idCertService, null, this.i18n, this.sink, this.eventFactory, null, null,
            this.userService, null, null, null, null, this.ownerCurator,
            this.activationKeyCurator,
            null, this.complianceRules, this.deletedConsumerCurator,
            null, null, new CandlepinCommonTestConfig());

        hypervisorResource = new HypervisorResource(consumerResource,
            consumerCurator, this.deletedConsumerCurator, i18n);

        // Ensure that we get the consumer that was passed in back from the create call.
        when(consumerCurator.create(any(Consumer.class))).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return invocation.getArguments()[0];
            }
        });
        when(complianceRules.getStatus(any(Consumer.class), any(Date.class)))
            .thenReturn(new ComplianceStatus(new Date()));
    }

    @Test
    public void hypervisorCheckInCreatesNewConsumer() throws Exception {
        Owner owner = new Owner("admin");

        Map<String, List<GuestId>> hostGuestMap = new HashMap<String, List<GuestId>>();
        hostGuestMap.put("test-host", Arrays.asList(new GuestId("GUEST_A"),
            new GuestId("GUEST_B")));

        when(consumerCurator.findByUuid(eq("test-host"))).thenReturn(null);
        when(ownerCurator.lookupByKey(eq(owner.getKey()))).thenReturn(owner);
        when(principal.canAccess(eq(owner), eq(Access.ALL))).thenReturn(true);
        when(consumerTypeCurator.lookupByLabel(
            eq(ConsumerTypeEnum.HYPERVISOR.getLabel()))).thenReturn(hypervisorType);
        when(idCertService.generateIdentityCert(any(Consumer.class)))
            .thenReturn(new IdentityCertificate());

        HypervisorCheckInResult result = hypervisorResource.hypervisorCheckIn(hostGuestMap,
            principal, owner.getKey());

        Set<Consumer> created = result.getCreated();
        assertEquals(1, created.size());

        Consumer c1 = created.iterator().next();
        assertEquals("test-host", c1.getUuid());
        assertEquals(2, c1.getGuestIds().size());
        assertEquals("GUEST_A", c1.getGuestIds().get(0).getGuestId());
        assertEquals("GUEST_B", c1.getGuestIds().get(1).getGuestId());
        assertEquals("x86_64", c1.getFact("uname.machine"));
        assertEquals("hypervisor", c1.getType().getLabel());
    }

    @Test
    public void hypervisorCheckInUpdatesGuestIdsWhenHostConsumerExists() throws Exception {
        Owner owner = new Owner("admin");

        Map<String, List<GuestId>> hostGuestMap = new HashMap<String, List<GuestId>>();
        hostGuestMap.put("test-host", Arrays.asList(new GuestId("GUEST_B")));

        Owner o = new Owner();
        o.setId("owner-id");
        Consumer existing = new Consumer();
        existing.setUuid("test-host");
        existing.setOwner(o);
        existing.addGuestId(new GuestId("GUEST_A"));

        when(consumerCurator.findByUuid(eq("test-host"))).thenReturn(existing);

        HypervisorCheckInResult result = hypervisorResource.hypervisorCheckIn(hostGuestMap,
            principal, owner.getKey());
        Set<Consumer> updated = result.getUpdated();
        assertEquals(1, updated.size());

        Consumer c1 = updated.iterator().next();
        assertEquals("test-host", c1.getUuid());
        assertEquals(1, c1.getGuestIds().size());
        assertEquals("GUEST_B", c1.getGuestIds().get(0).getGuestId());
    }

    @Test
    public void hypervisorCheckInReportsFailuresOnCreateFailure() {
        Owner owner = new Owner("admin");

        Map<String, List<GuestId>> hostGuestMap = new HashMap<String, List<GuestId>>();
        String expectedHostVirtId = "test-host-id";
        hostGuestMap.put(expectedHostVirtId, Arrays.asList(new GuestId("GUEST_A"),
            new GuestId("GUEST_B")));

        // Force create.
        when(consumerCurator.findByUuid(eq(expectedHostVirtId))).thenReturn(null);

        String expectedMessage = "Forced Exception.";
        RuntimeException exception = new RuntimeException(expectedMessage);
        // Simulate failure  when checking the owner
        when(ownerCurator.lookupByKey(eq(owner.getKey()))).thenThrow(exception);

        HypervisorCheckInResult result = hypervisorResource.hypervisorCheckIn(hostGuestMap,
            principal, owner.getKey());

        Set<String> failures = result.getFailedUpdate();
        assertEquals(1, failures.size());
        assertEquals(expectedHostVirtId + ": " + expectedMessage,
            failures.iterator().next());
    }

    @Test
    public void hypervisorCheckInReportsFailureWhenGuestIdUpdateFails() throws Exception {
        Owner owner = new Owner("admin");

        Map<String, List<GuestId>> hostGuestMap = new HashMap<String, List<GuestId>>();
        String expectedHostVirtId = "test-host";
        hostGuestMap.put(expectedHostVirtId, Arrays.asList(new GuestId("GUEST_B")));

        Consumer existing = new Consumer();
        existing.setUuid(expectedHostVirtId);
        existing.addGuestId(new GuestId("GUEST_A"));

        // Force update
        when(consumerCurator.findByUuid(eq(expectedHostVirtId))).thenReturn(existing);

        String expectedMessage = "Forced Exception.";
        RuntimeException exception = new RuntimeException(expectedMessage);
        // Simulate failure  when checking the owner
        when(consumerCurator.getHost(any(String.class))).thenThrow(exception);

        HypervisorCheckInResult result = hypervisorResource.hypervisorCheckIn(hostGuestMap,
            principal, owner.getKey());

        Set<String> failures = result.getFailedUpdate();
        assertEquals(1, failures.size());
        assertEquals(expectedHostVirtId + ": " + expectedMessage,
            failures.iterator().next());
    }

}
