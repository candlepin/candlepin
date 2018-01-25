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
package org.candlepin.resource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.auth.permissions.PermissionFactory;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.controller.ContentManager;
import org.candlepin.controller.ProductManager;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.Role;
import org.candlepin.model.RoleCurator;
import org.candlepin.model.UeberCertificate;
import org.candlepin.model.UeberCertificateCurator;
import org.candlepin.model.UeberCertificateGenerator;
import org.candlepin.model.User;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.util.ContentOverrideValidator;
import org.candlepin.util.ServiceLevelValidator;

import org.junit.Before;
import org.junit.Test;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;

import javax.inject.Inject;

/**
 * OwnerResourceUeberCertOperationsTest
 */
public class OwnerResourceUeberCertOperationsTest extends DatabaseTestFixture {
    private static final String OWNER_NAME = "Jar_Jar_Binks";

    @Inject private OwnerCurator ownerCurator;
    @Inject private ProductCurator productCurator;
    @Inject private ConsumerCurator consumerCurator;
    @Inject private ConsumerTypeCurator consumerTypeCurator;
    @Inject private EntitlementCurator entitlementCurator;
    @Inject private RoleCurator roleCurator;
    @Inject private EntitlementCertificateCurator entCertCurator;
    @Inject private CandlepinPoolManager poolManager;
    @Inject private UeberCertificateGenerator ueberCertGenerator;
    @Inject private UeberCertificateCurator ueberCertCurator;
    @Inject private PermissionFactory permFactory;
    @Inject private ServiceLevelValidator serviceLevelValidator;
    @Inject private I18n i18n;
    @Inject private ContentOverrideValidator contentOverrideValidator;
    @Inject private ProductManager productManager;
    @Inject private ContentManager contentManager;

    private Owner owner;
    private OwnerResource or;

    private Principal principal;

    @Before
    public void setUp() {
        owner = ownerCurator.create(new Owner(OWNER_NAME));

        Role ownerAdminRole = createAdminRole(owner);
        roleCurator.create(ownerAdminRole);

        User user = new User("testing user", "pass");
        principal = new UserPrincipal("testing user",
            new ArrayList<Permission>(permFactory.createPermissions(user,
                ownerAdminRole.getPermissions())), false);
        setupPrincipal(principal);

        or = new OwnerResource(
            ownerCurator, productCurator, null, consumerCurator, i18n, null, null, null,
            null, null, poolManager, null, null,
            null, null, consumerTypeCurator, entCertCurator, entitlementCurator, ueberCertCurator,
            ueberCertGenerator, null,  null, contentOverrideValidator, serviceLevelValidator, null, null,
            null, null, null, this.modelTranslator);
    }

    @Test
    public void testUeberCertIsRegeneratedOnNextInvocation() throws Exception {
        UeberCertificate firstCert = or.createUeberCertificate(principal, owner.getKey());
        UeberCertificate secondCert = or.createUeberCertificate(principal, owner.getKey());
        assertTrue(firstCert.getId() != secondCert.getId());
    }

    @Test(expected = NotFoundException.class)
    public void certificateGenerationRaisesExceptionIfOwnerNotFound() throws Exception {
        or.createUeberCertificate(principal, "non-existant");
    }

    @Test(expected = NotFoundException.class)
    public void certificateRetrievalRaisesExceptionIfOwnerNotFound() throws Exception {
        or.getUeberCertificate(principal, "non-existant");
    }

    @Test(expected = NotFoundException.class)
    public void certificateRetrievalRaisesExceptionIfNoCertificateWasGenerated()
        throws Exception {
        // verify that owner under test doesn't have a certificate
        Owner anotherOwner = ownerCurator.create(new Owner(OWNER_NAME + "1"));
        or.getUeberCertificate(principal, anotherOwner.getKey());
    }

    @Test
    public void certificateRetrievalReturnsCert() {
        UeberCertificate generated = or.createUeberCertificate(principal, owner.getKey());
        assertNotNull(generated);

        UeberCertificate retrieved = or.getUeberCertificate(principal, owner.getKey());
        assertNotNull(retrieved);

        assertEquals(generated, retrieved);
    }

}
