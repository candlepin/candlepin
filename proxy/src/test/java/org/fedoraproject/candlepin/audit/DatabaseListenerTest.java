/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.audit;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.fedoraproject.candlepin.model.EventCurator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;


/**
 * DatabaseListenerTest
 */
@RunWith(MockitoJUnitRunner.class)
public class DatabaseListenerTest {

    @Mock private EventCurator ec;
    @Mock private Event event;
    private DatabaseListener dl;

    @Before
    public void init() {
        dl = new DatabaseListener(ec);
    }

    @Test
    public void onEvent() {
        dl.onEvent(event);
        verify(ec).create(event);
    }

    @Test
    public void eventNull() {
        when(ec.create(any(Event.class))).thenThrow(new NullPointerException());
        dl.onEvent(null);
        verify(ec, never()).create(any(Event.class));
    }

    @Test(expected = NullPointerException.class)
    public void curatorNull() {
        DatabaseListener localdl = new DatabaseListener(null);
        localdl.onEvent(event);
    }
}
