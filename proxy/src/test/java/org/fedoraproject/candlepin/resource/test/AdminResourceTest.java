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
package org.fedoraproject.candlepin.resource.test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.ConsumerTypeCurator;
import org.fedoraproject.candlepin.model.User;
import org.fedoraproject.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.fedoraproject.candlepin.resource.AdminResource;
import org.fedoraproject.candlepin.service.UserServiceAdapter;

import org.junit.Before;
import org.junit.Test;


/**
 * AdminResourceTest
 */
public class AdminResourceTest {

    private ConsumerTypeCurator ctc;
    private UserServiceAdapter usa;
    private AdminResource ar;
    
    @Before
    public void init() {
        ctc = mock(ConsumerTypeCurator.class);
        usa = mock(UserServiceAdapter.class);
        ar = new AdminResource(ctc, usa);
    }
    
    @Test
    public void initialize() {
        when(ctc.lookupByLabel(ConsumerTypeEnum.SYSTEM.getLabel())).thenReturn(null);
        assertEquals("Initialized!", ar.initialize());
        verify(ctc, times(ConsumerTypeEnum.values().length)).create(
            any(ConsumerType.class));
        verify(usa).createUser(any(User.class));
    }
    
    @Test
    public void initWithException() {
        when(ctc.lookupByLabel(ConsumerTypeEnum.SYSTEM.getLabel())).thenReturn(null);
        when(usa.createUser(any(User.class))).thenThrow(
            new UnsupportedOperationException());
        assertEquals("Initialized!", ar.initialize());
    }
    
    @Test
    public void alreadyInitialized() {
        ConsumerType ct = mock(ConsumerType.class);
        when(ctc.lookupByLabel(ConsumerTypeEnum.SYSTEM.getLabel())).thenReturn(ct);
        assertEquals("Already initialized.", ar.initialize());
    }
}
