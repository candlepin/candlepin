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
package org.candlepin.model;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.auth.Access;
import org.candlepin.auth.ConsumerPrincipal;
import org.candlepin.auth.Principal;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.resource.OwnerResource;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

/**
 * OwnerAccessTest
 */
public class OwnerAccessControlTest extends DatabaseTestFixture {
    @Inject private OwnerCurator ownerCurator;
    @Inject private OwnerResource resource;
    private PrincipalProvider principalProvider;

    private Owner owner;

    @BeforeEach
    @Override
    public void init() throws Exception {
        super.init();
        this.owner = createOwner();
        this.principalProvider = mock(PrincipalProvider.class);
    }

    @Test
    public void superAdminCanCreateAnOwner() {
        setupAdminPrincipal("dude");
        securityInterceptor.enable();

        OwnerDTO dto = new OwnerDTO();
        dto.setKey("Test Owner");
        dto.setDisplayName("Test Owner");

        dto = resource.createOwner(dto);

        assertNotNull(dto.getId());
        assertNotNull(ownerCurator.get(dto.getId()));
    }

    @Test
    public void ownerAdminCannotCreateAnOwner() {
        Principal principal = setupPrincipal(owner, Access.ALL);
        when(this.principalProvider.get()).thenReturn(principal);
        securityInterceptor.enable();

        OwnerDTO dto = new OwnerDTO();
        dto.setKey("Test Owner");
        dto.setDisplayName("Test Owner");

        assertThrows(ForbiddenException.class, () -> resource.createOwner(dto));
    }

    @Test
    public void consumerCannotCreateAnOwner() {
        Consumer consumer = createConsumer(owner);
        Principal principal = setupPrincipal(new ConsumerPrincipal(consumer, owner));
        when(this.principalProvider.get()).thenReturn(principal);
        securityInterceptor.enable();

        OwnerDTO dto = new OwnerDTO();
        dto.setKey("Test Owner");
        dto.setDisplayName("Test Owner");

        assertThrows(ForbiddenException.class, () -> resource.createOwner(dto));
    }
}
