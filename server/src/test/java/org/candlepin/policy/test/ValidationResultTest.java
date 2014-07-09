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
package org.candlepin.policy.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.candlepin.policy.ValidationError;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.ValidationWarning;
import org.junit.Before;
import org.junit.Test;


/**
 * ValidationResultTest
 */
public class ValidationResultTest {
    private ValidationResult vr;

    @Before
    public void init() {
        vr = new ValidationResult();
    }

    @Test
    public void emptyErrors() {
        assertNotNull(vr.getErrors());
        assertTrue(vr.getErrors().isEmpty());
    }

    @Test
    public void addErrorsNull() {
        vr.addError((ValidationError) null);
        assertFalse(vr.getErrors().isEmpty());
        assertTrue(null == vr.getErrors().get(0));
    }

    @Test
    public void addErrorString() {
        vr.addError("error1");
        assertFalse(vr.getErrors().isEmpty());
        assertFalse(vr.getErrors().isEmpty());
    }

    @Test
    public void addError() {
        ValidationError ve = mock(ValidationError.class);
        vr.addError(ve);
        assertFalse(vr.getErrors().isEmpty());
        assertEquals(ve, vr.getErrors().get(0));
    }

    @Test
    public void getWarnings() {
        assertTrue(vr.getWarnings().isEmpty());
    }

    @Test
    public void addWarning() {
        ValidationWarning vw = mock(ValidationWarning.class);
        vr.addWarning(vw);
        assertFalse(vr.getWarnings().isEmpty());
        assertEquals(vw, vr.getWarnings().get(0));
    }

    @Test
    public void addWarningString() {
        vr.addWarning("warning");
        assertFalse(vr.getWarnings().isEmpty());
        assertEquals("warning", vr.getWarnings().get(0).getResourceKey());
    }

    @Test
    public void hasErrors() {
        assertFalse(vr.hasErrors());
        vr.addError("error");
        assertTrue(vr.hasErrors());
    }

    @Test
    public void hasWarnings() {
        assertFalse(vr.hasWarnings());
        vr.addWarning("warning");
        assertTrue(vr.hasWarnings());
    }

    @Test
    public void isSuccessful() {
        assertTrue(vr.isSuccessful());
        vr.addError("unsuccessful");
        assertFalse(vr.isSuccessful());
    }
}
