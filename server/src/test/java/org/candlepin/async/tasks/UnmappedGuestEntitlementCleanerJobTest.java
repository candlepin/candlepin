/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.async.tasks;

import org.candlepin.async.JobExecutionContext;
import org.candlepin.controller.Entitler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test suite for the UnmappedGuestEntitlementCleanerJob class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class UnmappedGuestEntitlementCleanerJobTest {

    private Entitler entitler;

    @BeforeEach
    public void init() {
        this.entitler = mock(Entitler.class);
    }

    private UnmappedGuestEntitlementCleanerJob createJobInstance() {
        return new UnmappedGuestEntitlementCleanerJob(this.entitler);
    }

    @Test
    public void executeReapedSomeEntitlements() throws Exception {
        when(entitler.revokeUnmappedGuestEntitlements()).thenReturn(10);
        JobExecutionContext context = mock(JobExecutionContext.class);
        UnmappedGuestEntitlementCleanerJob job = this.createJobInstance();
        String result = (String) job.execute(context);

        assertEquals("Reaped 10 unmapped guest entitlements due to expiration.", result);
    }

    @Test
    public void executeReapedNoEntitlements() throws Exception {
        when(entitler.revokeUnmappedGuestEntitlements()).thenReturn(0);
        JobExecutionContext context = mock(JobExecutionContext.class);
        UnmappedGuestEntitlementCleanerJob job = this.createJobInstance();
        String result = (String) job.execute(context);

        assertEquals("No unmapped guest entitlements need reaping.", result);
    }
}
