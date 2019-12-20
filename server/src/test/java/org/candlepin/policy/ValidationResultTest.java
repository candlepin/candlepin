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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

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
    public void addErrorNull() {
        vr.addError((RulesValidationError) null);
        assertFalse(vr.getErrors().isEmpty());
        assertNull(vr.getErrors().get(0));
    }

    @Test
    public void addError() {
        RulesValidationError ve = mock(RulesValidationError.class);
        vr.addError(ve);
        assertFalse(vr.getErrors().isEmpty());
        assertEquals(ve, vr.getErrors().get(0));
    }

    @Test
    public void hasErrors() {
        assertFalse(vr.hasErrors());
        vr.addError(mock(RulesValidationError.class));
        assertTrue(vr.hasErrors());
    }

    @Test
    public void emptyWarnings() {
        assertNotNull(vr.getWarnings());
        assertTrue(vr.getWarnings().isEmpty());
    }

    @Test
    public void addWarningNull() {
        vr.addWarning(null);
        assertFalse(vr.getWarnings().isEmpty());
        assertNull(vr.getWarnings().get(0));
    }

    @Test
    public void addWarning() {
        RulesValidationWarning vw = mock(RulesValidationWarning.class);
        vr.addWarning(vw);
        assertFalse(vr.getWarnings().isEmpty());
        assertEquals(vw, vr.getWarnings().get(0));
    }

    @Test
    public void hasWarnings() {
        assertFalse(vr.hasWarnings());
        vr.addWarning(mock(RulesValidationWarning.class));
        assertTrue(vr.hasWarnings());
    }

    @Test
    public void isSuccessful() {
        assertTrue(vr.isSuccessful());
        vr.addError((i18n, args) -> "mock error message");
        assertFalse(vr.isSuccessful());
    }

    @Test
    public void isSuccessfulhnejknh() {
        vr.addError((i18n, args) -> "mock error message");
        assertFalse(vr.isSuccessful());
    }
}
