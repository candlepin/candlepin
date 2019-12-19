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

package org.candlepin.util;

import org.candlepin.common.exceptions.SlaValidationException;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xnap.commons.i18n.I18n;

import java.util.Arrays;
import java.util.HashSet;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class ServiceLevelValidatorTest {

    private static final String OWNER_ID = "test_owner";

    private I18n i18n;
    private PoolManager poolManager;
    private OwnerCurator ownerCurator;

    @BeforeEach
    void setUp() {
        this.i18n = spy(new I18n(mock(ResourceBundle.class)));
        this.poolManager = mock(PoolManager.class);
        this.ownerCurator = mock(OwnerCurator.class);

        doReturn(
            new HashSet<>(
                Arrays.asList(
                    "Standard", "Premium", "Platinum")))
            .when(this.poolManager)
            .retrieveServiceLevelsForOwner(eq(OWNER_ID), eq(false));
        doReturn(newOwner(OWNER_ID))
            .when(this.ownerCurator)
            .findOwnerById(eq(OWNER_ID));
    }

    private Owner newOwner(String ownerId) {
        Owner owner = new Owner(ownerId, "test_owner");
        owner.setId(ownerId);
        return owner;
    }

    @Test
    void invalid() {
        ServiceLevelValidator validator = new ServiceLevelValidator(
            i18n, poolManager, ownerCurator);

        assertThrows(SlaValidationException.class,
            () -> validator.validate(OWNER_ID, "mySLA"));
    }

    @Test
    void valid() {
        ServiceLevelValidator validator = new ServiceLevelValidator(
            i18n, poolManager, ownerCurator);

        assertDoesNotThrow(() -> validator.validate(OWNER_ID, "Premium"));
    }

}
