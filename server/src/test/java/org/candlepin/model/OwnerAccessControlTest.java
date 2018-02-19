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

import static org.junit.Assert.assertNotNull;

import org.candlepin.auth.Access;
import org.candlepin.auth.ConsumerPrincipal;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.resource.OwnerResource;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.Before;
import org.junit.Test;

import javax.inject.Inject;

/**
 * OwnerAccessTest
 */
public class OwnerAccessControlTest extends DatabaseTestFixture {
    @Inject private OwnerCurator ownerCurator;
    @Inject private OwnerResource resource;

    private Owner owner;

    @Before
    @Override
    public void init() throws Exception {
        super.init();
        this.owner = createOwner();
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
        assertNotNull(ownerCurator.find(dto.getId()));
    }

    @Test(expected = ForbiddenException.class)
    public void ownerAdminCannotCreateAnOwner() {
        setupPrincipal(owner, Access.ALL);
        securityInterceptor.enable();

        OwnerDTO dto = new OwnerDTO();
        dto.setKey("Test Owner");
        dto.setDisplayName("Test Owner");

        resource.createOwner(dto);
    }

    @Test(expected = ForbiddenException.class)
    public void consumerCannotCreateAnOwner() {
        Consumer consumer = createConsumer(owner);
        setupPrincipal(new ConsumerPrincipal(consumer));
        securityInterceptor.enable();

        OwnerDTO dto = new OwnerDTO();
        dto.setKey("Test Owner");
        dto.setDisplayName("Test Owner");

        resource.createOwner(dto);
    }
}
