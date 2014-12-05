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

package org.candlepin.gutterball.eventhandler;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.*;

import org.candlepin.gutterball.curator.ComplianceSnapshotCurator;
import org.candlepin.gutterball.curator.ConsumerStateCurator;
import org.candlepin.gutterball.model.ConsumerState;
import org.candlepin.gutterball.model.Event;
import org.candlepin.gutterball.model.snapshot.Compliance;
import org.candlepin.gutterball.model.snapshot.ComplianceStatus;
import org.candlepin.gutterball.model.snapshot.Consumer;
import org.candlepin.gutterball.model.snapshot.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Date;

@RunWith(MockitoJUnitRunner.class)
public class ComplianceHandlerTest {

    @Mock
    private ObjectMapper mapper;

    @Mock
    private ComplianceSnapshotCurator complianceCurator;

    @Mock
    private ConsumerStateCurator consumerStateCurator;

    private ComplianceHandler handler;

    @Test
    public void testHandleCreated() throws Exception {
        handler = new ComplianceHandler(this.mapper, this.complianceCurator, this.consumerStateCurator);

        Event event = new Event();
        event.setNewEntity("test-string");

        Date expectedDate = new Date();

        String uuid = "a-very-unique-uuid";
        Owner owner = new Owner("123", "test_owner");

        // Consumer state is null here -- expected to be filled in by the event handler
        Consumer consumer = new Consumer(uuid, null, owner);
        ConsumerState cstate = new ConsumerState(uuid, owner.getKey(), expectedDate);

        ComplianceStatus status = new ComplianceStatus(expectedDate, "VALID");
        // Date is null here -- expected to be filled in with the status date.
        Compliance snap = new Compliance(null, consumer, status);

        when(mapper.readValue(eq(event.getNewEntity()), eq(Compliance.class))).thenReturn(snap);
        when(this.consumerStateCurator.findByUuid(eq(uuid))).thenReturn(cstate);
        handler.handleCreated(event);

        verify(complianceCurator).create(eq(snap));
        assertEquals(snap.getStatus().getDate(), snap.getDate());
        assertEquals(snap.getConsumer().getConsumerState(), cstate);
    }

    @Test
    public void testHandleCreatedWithoutConsumerState() throws Exception {
        handler = new ComplianceHandler(this.mapper, this.complianceCurator, this.consumerStateCurator);

        Event event = new Event();
        event.setNewEntity("test-string");

        Date expectedDate = new Date();

        String uuid = "a-very-unique-uuid";
        Owner owner = new Owner("123", "test_owner");

        // Consumer state is null here -- expected to be filled in by the event handler
        Consumer consumer = new Consumer(uuid, null, owner);

        ComplianceStatus status = new ComplianceStatus(expectedDate, "VALID");
        // Date is null here -- expected to be filled in with the status date.
        Compliance snap = new Compliance(null, consumer, status);

        when(mapper.readValue(eq(event.getNewEntity()), eq(Compliance.class))).thenReturn(snap);
        when(this.consumerStateCurator.create(isA(ConsumerState.class))).then(returnsFirstArg());

        handler.handleCreated(event);

        verify(complianceCurator).create(eq(snap));
        verify(this.consumerStateCurator).create(isA(ConsumerState.class));
        assertEquals(snap.getStatus().getDate(), snap.getDate());

        ConsumerState actual = snap.getConsumer().getConsumerState();
        assertNotNull(actual);
        assertEquals(uuid, actual.getUuid());
        assertEquals(owner.getKey(), actual.getOwnerKey());
        assertEquals(expectedDate, actual.getCreated());
    }

    @Test
    public void testHandleUpdatedDoesNothing() {
        verifyZeroInteractions(complianceCurator, mapper);
    }

    @Test
    public void testHandleDeletedDoesNothing() {
        verifyZeroInteractions(complianceCurator, mapper);
    }


}
