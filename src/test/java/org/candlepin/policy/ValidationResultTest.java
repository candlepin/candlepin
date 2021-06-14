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
package org.candlepin.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ValidationResultTest
 */
public class ValidationResultTest {
    private ValidationResult vr;

    @BeforeEach
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
    public void emptyErrorKeys() {
        assertNotNull(vr.getErrorKeys());
        assertTrue(vr.getErrorKeys().isEmpty());
    }

    @Test
    public void addErrorKeyNull() {
        vr.addError((RulesValidationError) null);
        assertFalse(vr.getErrorKeys().isEmpty());
        assertNull(vr.getErrorKeys().get(0));
    }

    @Test
    public void addErrorKey() {
        RulesValidationError ve = mock(RulesValidationError.class);
        vr.addError(ve);
        assertFalse(vr.getErrorKeys().isEmpty());
        assertEquals(ve, vr.getErrorKeys().get(0));
    }

    @Test
    public void hasErrorKeys() {
        assertFalse(vr.hasErrorKeys());
        vr.addError(mock(RulesValidationError.class));
        assertTrue(vr.hasErrorKeys());
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
