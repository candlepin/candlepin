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
package org.candlepin.controller.mode;

// import static org.hamcrest.MatcherAssert.*;
// import static org.hamcrest.Matchers.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.candlepin.controller.mode.CandlepinModeManager.Mode;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Iterator;
import java.util.Set;



/**
 * Test suite for the CandlepinModeManager
 */
public class CandlepinModeManagerTest {

    public CandlepinModeManager buildModeManager() {
        return new CandlepinModeManager();
    }

    @Test
    public void testDefaultsToNormalMode() {
        CandlepinModeManager manager = this.buildModeManager();

        assertEquals(Mode.NORMAL, manager.getCurrentMode());
    }

    @Test
    public void testEntersSuspendMode() {
        CandlepinModeManager manager = this.buildModeManager();

        manager.suspendOperations("reason_class-1", "test reason");

        assertEquals(Mode.SUSPEND, manager.getCurrentMode());
    }

    @Test
    public void testResumesNormalMode() {
        String reasonClass = "reason_class-1";
        String reason = "test_reason";

        CandlepinModeManager manager = this.buildModeManager();

        manager.suspendOperations(reasonClass, reason);
        assertEquals(Mode.SUSPEND, manager.getCurrentMode());

        manager.resolveSuspendReason(reasonClass, reason);
        assertEquals(Mode.NORMAL, manager.getCurrentMode());
    }

    @Test
    public void testResumesMultipleReasonsOfOneClass() {
        String reasonClass = "reason_class-1";
        String reason1 = "test_reason-1";
        String reason2 = "test_reason-2";

        CandlepinModeManager manager = this.buildModeManager();

        manager.suspendOperations(reasonClass, reason1);
        assertEquals(Mode.SUSPEND, manager.getCurrentMode());

        manager.suspendOperations(reasonClass, reason2);
        assertEquals(Mode.SUSPEND, manager.getCurrentMode());

        manager.resolveSuspendReasonClass(reasonClass);
        assertEquals(Mode.NORMAL, manager.getCurrentMode());
    }

    @Test
    public void testResolvingReasonDoesNotResumeWhenOtherReasonsRemain() {
        String reasonClass = "reason_class-1";
        String reason1 = "test_reason-1";
        String reason2 = "test_reason-2";

        CandlepinModeManager manager = this.buildModeManager();

        manager.suspendOperations(reasonClass, reason1);
        assertEquals(Mode.SUSPEND, manager.getCurrentMode());

        manager.suspendOperations(reasonClass, reason2);
        assertEquals(Mode.SUSPEND, manager.getCurrentMode());

        manager.resolveSuspendReason(reasonClass, reason1);
        assertEquals(Mode.SUSPEND, manager.getCurrentMode());
    }

    @Test
    public void testResolvingReasonDoesNotResumeWhenOtherReasonClassesRemain() {
        String reasonClass1 = "reason_class-1";
        String reasonClass2 = "reason_class-2";
        String reason1 = "test_reason-1";
        String reason2 = "test_reason-2";

        CandlepinModeManager manager = this.buildModeManager();

        manager.suspendOperations(reasonClass1, reason1);
        assertEquals(Mode.SUSPEND, manager.getCurrentMode());

        manager.suspendOperations(reasonClass2, reason2);
        assertEquals(Mode.SUSPEND, manager.getCurrentMode());

        manager.resolveSuspendReason(reasonClass1, reason1);
        assertEquals(Mode.SUSPEND, manager.getCurrentMode());
    }

    @Test
    public void testResolvingReasonClassDoesNotResumeWhenOtherReasonsRemain() {
        String reasonClass1 = "reason_class-1";
        String reasonClass2 = "reason_class-2";
        String reason1 = "test_reason-1";
        String reason2 = "test_reason-2";

        CandlepinModeManager manager = this.buildModeManager();

        manager.suspendOperations(reasonClass1, reason1);
        assertEquals(Mode.SUSPEND, manager.getCurrentMode());

        manager.suspendOperations(reasonClass2, reason2);
        assertEquals(Mode.SUSPEND, manager.getCurrentMode());

        manager.resolveSuspendReasonClass(reasonClass1);
        assertEquals(Mode.SUSPEND, manager.getCurrentMode());
    }

    @Test
    public void testResolvingAllReasonClassesResumesNormalMode() {
        String reasonClass1 = "reason_class-1";
        String reasonClass2 = "reason_class-2";
        String reason1 = "test_reason-1";
        String reason2 = "test_reason-2";

        CandlepinModeManager manager = this.buildModeManager();

        manager.suspendOperations(reasonClass1, reason1);
        assertEquals(Mode.SUSPEND, manager.getCurrentMode());

        manager.suspendOperations(reasonClass2, reason2);
        assertEquals(Mode.SUSPEND, manager.getCurrentMode());

        manager.resolveSuspendReasonClass(reasonClass1);
        assertEquals(Mode.SUSPEND, manager.getCurrentMode());

        manager.resolveSuspendReasonClass(reasonClass2);
        assertEquals(Mode.NORMAL, manager.getCurrentMode());
    }

    @Test
    public void testResolvingInvalidReasonNoops() {
        CandlepinModeManager manager = this.buildModeManager();

        manager.resolveSuspendReason("some bad reason class", "some reason");
        assertEquals(Mode.NORMAL, manager.getCurrentMode());

        manager.suspendOperations("reason_class", "reason");
        assertEquals(Mode.SUSPEND, manager.getCurrentMode());

        manager.resolveSuspendReason("some bad reason class", "some reason");
        assertEquals(Mode.SUSPEND, manager.getCurrentMode());
    }

    @Test
    public void testResolvingInvalidClassReasonNoops() {
        CandlepinModeManager manager = this.buildModeManager();

        manager.resolveSuspendReasonClass("some bad reason class");
        assertEquals(Mode.NORMAL, manager.getCurrentMode());

        manager.suspendOperations("reason_class", "reason");
        assertEquals(Mode.SUSPEND, manager.getCurrentMode());

        manager.resolveSuspendReasonClass("some bad reason class");
        assertEquals(Mode.SUSPEND, manager.getCurrentMode());
    }

    @Test
    public void testListenersNotifiedOnSuspendTransition() {
        ModeChangeListener listener = mock(ModeChangeListener.class);

        ArgumentCaptor<CandlepinModeManager> mmCaptor = ArgumentCaptor.forClass(CandlepinModeManager.class);
        ArgumentCaptor<Mode> pmCaptor = ArgumentCaptor.forClass(Mode.class);
        ArgumentCaptor<Mode> cmCaptor = ArgumentCaptor.forClass(Mode.class);

        CandlepinModeManager manager = this.buildModeManager();
        manager.registerModeChangeListener(listener);

        manager.suspendOperations("reason_class-1", "reason-1");
        manager.suspendOperations("reason_class-1", "reason-2");
        manager.suspendOperations("reason_class-2", "reason-1");
        manager.suspendOperations("reason_class-2", "reason-2");

        verify(listener, times(1))
            .handleModeChange(mmCaptor.capture(), pmCaptor.capture(), cmCaptor.capture());

        CandlepinModeManager mmReceived = mmCaptor.getValue();
        Mode pmReceived = pmCaptor.getValue();
        Mode cmReceived = cmCaptor.getValue();

        assertSame(manager, mmReceived);
        assertEquals(Mode.NORMAL, pmReceived);
        assertEquals(Mode.SUSPEND, cmReceived);
    }

    @Test
    public void testListenersNotifiedOnNormalModeTransition() {
        ModeChangeListener listener = mock(ModeChangeListener.class);

        ArgumentCaptor<CandlepinModeManager> mmCaptor = ArgumentCaptor.forClass(CandlepinModeManager.class);
        ArgumentCaptor<Mode> pmCaptor = ArgumentCaptor.forClass(Mode.class);
        ArgumentCaptor<Mode> cmCaptor = ArgumentCaptor.forClass(Mode.class);

        CandlepinModeManager manager = this.buildModeManager();
        manager.registerModeChangeListener(listener);

        manager.suspendOperations("reason_class-1", "reason-1");
        manager.suspendOperations("reason_class-1", "reason-2");
        manager.suspendOperations("reason_class-2", "reason-1");
        manager.suspendOperations("reason_class-2", "reason-2");

        verify(listener, times(1)).handleModeChange(eq(manager), eq(Mode.NORMAL), eq(Mode.SUSPEND));
        reset(listener);

        manager.resolveSuspendReason("reason_class-1", "reason-1");
        manager.resolveSuspendReason("reason_class-1", "reason-2");
        manager.resolveSuspendReason("reason_class-2", "reason-1");
        manager.resolveSuspendReason("reason_class-2", "reason-2");

        verify(listener, times(1))
            .handleModeChange(mmCaptor.capture(), pmCaptor.capture(), cmCaptor.capture());

        CandlepinModeManager mmReceived = mmCaptor.getValue();
        Mode pmReceived = pmCaptor.getValue();
        Mode cmReceived = cmCaptor.getValue();

        assertSame(manager, mmReceived);
        assertEquals(Mode.SUSPEND, pmReceived);
        assertEquals(Mode.NORMAL, cmReceived);
    }

    @Test
    public void testListenersNotifiedOnNormalModeTransitionWithClassResolution() {
        ModeChangeListener listener = mock(ModeChangeListener.class);

        ArgumentCaptor<CandlepinModeManager> mmCaptor = ArgumentCaptor.forClass(CandlepinModeManager.class);
        ArgumentCaptor<Mode> pmCaptor = ArgumentCaptor.forClass(Mode.class);
        ArgumentCaptor<Mode> cmCaptor = ArgumentCaptor.forClass(Mode.class);

        CandlepinModeManager manager = this.buildModeManager();
        manager.registerModeChangeListener(listener);

        manager.suspendOperations("reason_class-1", "reason-1");
        manager.suspendOperations("reason_class-1", "reason-2");
        manager.suspendOperations("reason_class-2", "reason-1");
        manager.suspendOperations("reason_class-2", "reason-2");

        verify(listener, times(1)).handleModeChange(eq(manager), eq(Mode.NORMAL), eq(Mode.SUSPEND));
        reset(listener);

        manager.resolveSuspendReasonClass("reason_class-1");
        manager.resolveSuspendReasonClass("reason_class-2");

        verify(listener, times(1))
            .handleModeChange(mmCaptor.capture(), pmCaptor.capture(), cmCaptor.capture());

        CandlepinModeManager mmReceived = mmCaptor.getValue();
        Mode pmReceived = pmCaptor.getValue();
        Mode cmReceived = cmCaptor.getValue();

        assertSame(manager, mmReceived);
        assertEquals(Mode.SUSPEND, pmReceived);
        assertEquals(Mode.NORMAL, cmReceived);
    }

    @Test
    public void testCombinedReasonSetIncludesAllClassesAndReasons() {
        CandlepinModeManager manager = this.buildModeManager();

        manager.suspendOperations("reason_class-1", "reason-1");
        manager.suspendOperations("reason_class-1", "reason-2");
        manager.suspendOperations("reason_class-2", "reason-1");
        manager.suspendOperations("reason_class-2", "reason-2");

        Set<ModeChangeReason> reasons = manager.getModeChangeReasons();

        assertEquals(4, reasons.size());

        for (int i = 1; i <= 2; ++i) {
            for (int j = 1; j <= 2; ++j) {
                Iterator<ModeChangeReason> iterator = reasons.iterator();
                while (iterator.hasNext()) {
                    ModeChangeReason reason = iterator.next();

                    if (reason.getReasonClass().equals("reason_class-" + i) &&
                        reason.getReason().equals("reason-" + j)) {

                        iterator.remove();
                        break;
                    }
                }
            }
        }

        assertEquals(0, reasons.size());
    }
}
