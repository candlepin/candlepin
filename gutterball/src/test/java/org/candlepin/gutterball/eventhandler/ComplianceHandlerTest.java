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

import org.candlepin.gutterball.curator.ComplianceSnapshotCurator;
import org.candlepin.gutterball.model.Event;
import org.candlepin.gutterball.model.snapshot.ComplianceSnapshot;
import org.candlepin.gutterball.model.snapshot.ComplianceStatusSnapshot;
import org.candlepin.gutterball.model.snapshot.ConsumerSnapshot;

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

    private ComplianceHandler handler;

    @Test
    public void testHandleCreatedCreatesANewComplianceStatusAndSetsDateToStatusDate() throws Exception {
        handler = new ComplianceHandler(mapper, complianceCurator);

        Event event = new Event();
        event.setNewEntity("test-string");

        Date expectedDate = new Date();
        ComplianceStatusSnapshot status = new ComplianceStatusSnapshot(expectedDate, "VALID");
        // Date is null here -- expected to be filled in with the status date.
        ComplianceSnapshot snap = new ComplianceSnapshot(null, new ConsumerSnapshot(), status);

        when(mapper.readValue(eq(event.getNewEntity()), eq(ComplianceSnapshot.class))).thenReturn(snap);
        handler.handleCreated(event);

        verify(complianceCurator).create(eq(snap));
        assertEquals(snap.getStatus().getDate(), snap.getDate());
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
