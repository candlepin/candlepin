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
package org.candlepin.pinsetter.core;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import org.junit.Before;
import org.junit.Test;

/**
 * PinsetterConextListenerTest
 */
public class PinsetterContextListenerTest {
    private PinsetterContextListener listener;
    private PinsetterKernel kernel;
    private PinsetterException pe;

    @Before
    public void init() {
        pe = mock(PinsetterException.class);
        kernel = mock(PinsetterKernel.class);
        listener = new PinsetterContextListener(kernel);
    }

    @Test
    public void testContextDestroyed() throws PinsetterException {
        listener.contextDestroyed();
        verify(kernel, atLeastOnce()).shutdown();
        verifyZeroInteractions(pe);
    }

    @Test
    public void testContextInitialized() throws PinsetterException {
        listener.contextInitialized();
        verify(kernel, atLeastOnce()).startup();
        verifyZeroInteractions(pe);
    }

    @Test
    public void destroyedError() throws PinsetterException {
        doThrow(pe).when(kernel).shutdown();
        listener.contextDestroyed();
        verify(pe, atLeastOnce()).printStackTrace();
    }

    @Test
    public void initError() throws PinsetterException {
        doThrow(pe).when(kernel).startup();
        listener.contextInitialized();
        verify(pe, atLeastOnce()).printStackTrace();
    }
}
