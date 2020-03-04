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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.candlepin.async.JobManager;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.permissions.PermissionFactory;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.dto.api.v1.UeberCertificateDTO;
import org.candlepin.model.Owner;
import org.candlepin.model.Role;
import org.candlepin.model.UeberCertificateCurator;
import org.candlepin.model.UeberCertificateGenerator;
import org.candlepin.model.User;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.util.ContentOverrideValidator;
import org.candlepin.util.ServiceLevelValidator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import javax.inject.Inject;

/**
 * OwnerResourceUeberCertOperationsTest
 */
public class OwnerResourceUeberCertOperationsTest extends DatabaseTestFixture {
    private static final String OWNER_NAME = "Jar_Jar_Binks";

    @Inject private CandlepinPoolManager poolManager;
    @Inject private UeberCertificateGenerator ueberCertGenerator;
    @Inject private UeberCertificateCurator ueberCertCurator;
    @Inject private PermissionFactory permFactory;
    @Inject private ServiceLevelValidator serviceLevelValidator;
    @Inject private ContentOverrideValidator contentOverrideValidator;

    private JobManager jobManager;

    private Owner owner;
    private OwnerResource or;

    private Principal principal;

    @BeforeEach
    public void setUp() {
        owner = ownerCurator.create(new Owner(OWNER_NAME));

        Role ownerAdminRole = createAdminRole(owner);
        roleCurator.create(ownerAdminRole);

        User user = new User("testing user", "pass");
        principal = new UserPrincipal("testing user",
            new ArrayList<>(permFactory.createPermissions(user, ownerAdminRole.getPermissions())), false);
        setupPrincipal(principal);

        this.jobManager = mock(JobManager.class);

        or = new OwnerResource(
            ownerCurator, null, consumerCurator, i18n, null, null, null, null,
            null, poolManager, null, null,
            null, null, entitlementCurator,
            ueberCertCurator, ueberCertGenerator, null,  null, contentOverrideValidator,
            serviceLevelValidator, null, null, null, null, null, this.modelTranslator, this.jobManager,
            null);
    }

    @Test
    public void testUeberCertIsRegeneratedOnNextInvocation() throws Exception {
        UeberCertificateDTO firstCert = or.createUeberCertificate(principal, owner.getKey());
        UeberCertificateDTO secondCert = or.createUeberCertificate(principal, owner.getKey());
        assertNotSame(firstCert.getId(), secondCert.getId());
    }

    @Test
    public void certificateGenerationRaisesExceptionIfOwnerNotFound() throws Exception {
        assertThrows(NotFoundException.class, () ->
            or.createUeberCertificate(principal, "non-existant")
        );
    }

    @Test
    public void certificateRetrievalRaisesExceptionIfOwnerNotFound() throws Exception {
        assertThrows(NotFoundException.class, () ->
            or.getUeberCertificate(principal, "non-existant")
        );
    }

    @Test
    public void certificateRetrievalRaisesExceptionIfNoCertificateWasGenerated()
        throws Exception {
        // verify that owner under test doesn't have a certificate
        Owner anotherOwner = ownerCurator.create(new Owner(OWNER_NAME + "1"));
        assertThrows(NotFoundException.class, () ->
            or.getUeberCertificate(principal, anotherOwner.getKey())
        );
    }

    @Test
    public void certificateRetrievalReturnsCert() {
        UeberCertificateDTO generated = or.createUeberCertificate(principal, owner.getKey());
        assertNotNull(generated);

        UeberCertificateDTO retrieved = or.getUeberCertificate(principal, owner.getKey());
        assertNotNull(retrieved);

        assertEquals(generated, retrieved);
    }

}
