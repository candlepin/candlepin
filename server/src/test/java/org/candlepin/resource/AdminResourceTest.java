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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.audit.EventSink;
import org.candlepin.dto.api.v1.QueueStatus;
import org.candlepin.model.User;
import org.candlepin.model.UserCurator;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.service.impl.DefaultUserServiceAdapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * AdminResourceTest
 */
public class AdminResourceTest {

    private UserServiceAdapter usa;
    private AdminResource ar;
    private UserCurator uc;
    private EventSink sink;

    @BeforeEach
    public void init() {
        usa = mock(DefaultUserServiceAdapter.class);
        uc = mock(UserCurator.class);
        sink = mock(EventSink.class);
        ar = new AdminResource(usa, uc, sink);
    }

    @Test
    public void initialize() {
        when(uc.getUserCount()).thenReturn(new Long(0));
        assertEquals("Initialized!", ar.initialize());
        verify(usa).createUser(any(User.class));
    }

    @Test
    public void initWithNonDefaultUserService() {
        ar = new AdminResource(mock(UserServiceAdapter.class), uc, null);
        assertEquals("Already initialized.", ar.initialize());
    }

    @Test
    public void alreadyInitialized() {
        when(uc.getUserCount()).thenReturn(new Long(1000));
        assertEquals("Already initialized.", ar.initialize());
    }

    @Test
    public void testQueueStats() {
        when(sink.getQueueInfo()).thenReturn(new ArrayList<>());
        assertEquals(0, ar.getQueueStats().size());

        List<QueueStatus> mockQueueStats = new ArrayList();
        mockQueueStats.add(new QueueStatus().queueName("test-1").pendingMessageCount(10L));
        mockQueueStats.add(new QueueStatus().queueName("test-2").pendingMessageCount(2L));

        when(sink.getQueueInfo()).thenReturn(mockQueueStats);
        assertEquals(2, ar.getQueueStats().size());
    }
}
