/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
import static org.mockito.Mockito.when;

import org.candlepin.async.JobManager;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.controller.ConsumerManager;
import org.candlepin.controller.ContentAccessManager;
import org.candlepin.controller.ManifestManager;
import org.candlepin.controller.OwnerManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.PoolService;
import org.candlepin.dto.api.server.v1.UeberCertificateDTO;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.ExporterMetadataCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Role;
import org.candlepin.model.UeberCertificateCurator;
import org.candlepin.model.User;
import org.candlepin.paging.PagingUtilFactory;
import org.candlepin.pki.certs.UeberCertificateGenerator;
import org.candlepin.resource.util.CalculatedAttributesUtil;
import org.candlepin.resource.util.ConsumerTypeValidator;
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.service.OwnerServiceAdapter;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.util.ContentOverrideValidator;
import org.candlepin.util.ServiceLevelValidator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;


public class OwnerResourceUeberCertOperationsTest extends DatabaseTestFixture {
    private static final String OWNER_NAME = "Jar_Jar_Binks";

    private PoolManager poolManager;
    private UeberCertificateGenerator ueberCertGenerator;
    private UeberCertificateCurator ueberCertCurator;
    private ServiceLevelValidator serviceLevelValidator;
    private ContentOverrideValidator contentOverrideValidator;
    private PoolService poolService;
    private EventSink sink;
    private EventFactory eventFactory;
    private ContentAccessManager contentAccessManager;
    private ManifestManager manifestManager;
    private OwnerManager ownerManager;
    private ExporterMetadataCurator exportCurator;
    private OwnerServiceAdapter ownerService;
    private CalculatedAttributesUtil calculatedAttributesUtil;
    private ConsumerTypeValidator consumerTypeValdator;
    private DTOValidator dtoValidator;
    private PagingUtilFactory pagingUtilFactory;
    private ConsumerManager consumerManager;

    private PrincipalProvider principalProvider;
    private JobManager jobManager;

    private Owner owner;
    private OwnerResource or;

    private Principal principal;

    @BeforeEach
    public void setUp() {
        poolManager = this.injector.getInstance(PoolManager.class);
        ueberCertGenerator = this.injector.getInstance(UeberCertificateGenerator.class);
        ueberCertCurator = this.injector.getInstance(UeberCertificateCurator.class);
        serviceLevelValidator = this.injector.getInstance(ServiceLevelValidator.class);
        contentOverrideValidator = this.injector.getInstance(ContentOverrideValidator.class);
        poolService = this.injector.getInstance(PoolService.class);
        sink = this.injector.getInstance(EventSink.class);
        eventFactory = this.injector.getInstance(EventFactory.class);
        contentAccessManager = this.injector.getInstance(ContentAccessManager.class);
        manifestManager = this.injector.getInstance(ManifestManager.class);
        ownerManager = this.injector.getInstance(OwnerManager.class);
        exportCurator = this.injector.getInstance(ExporterMetadataCurator.class);
        ownerService = this.injector.getInstance(OwnerServiceAdapter.class);
        calculatedAttributesUtil = this.injector.getInstance(CalculatedAttributesUtil.class);
        consumerTypeValdator = this.injector.getInstance(ConsumerTypeValidator.class);
        dtoValidator = this.injector.getInstance(DTOValidator.class);
        pagingUtilFactory = this.injector.getInstance(PagingUtilFactory.class);
        consumerManager = this.injector.getInstance(ConsumerManager.class);

        owner = ownerCurator.create(new Owner()
            .setKey(OWNER_NAME)
            .setDisplayName(OWNER_NAME));

        Role ownerAdminRole = createAdminRole(owner);
        roleCurator.create(ownerAdminRole);

        User user = new User("testing user", "pass");
        principal = new UserPrincipal("testing user",
            new ArrayList<>(permissionFactory.createPermissions(user, ownerAdminRole.getPermissions())),
            false);
        setupPrincipal(principal);

        this.jobManager = mock(JobManager.class);
        this.principalProvider = mock(PrincipalProvider.class);

        or = new OwnerResource(
            ownerCurator, activationKeyCurator, consumerCurator, consumerManager, i18n, sink, eventFactory,
            contentAccessManager, manifestManager, poolManager, poolService, poolCurator, ownerManager,
            exportCurator, ownerInfoCurator, importRecordCurator, entitlementCurator, ueberCertCurator,
            ueberCertGenerator, environmentCurator, calculatedAttributesUtil, contentOverrideValidator,
            serviceLevelValidator, ownerService, config, consumerTypeValdator, productCurator,
            this.modelTranslator, this.jobManager, dtoValidator, this.principalProvider,
            this.pagingUtilFactory);
    }

    @Test
    public void testUeberCertIsRegeneratedOnNextInvocation() {
        when(this.principalProvider.get()).thenReturn(principal);
        UeberCertificateDTO firstCert = or.createUeberCertificate(owner.getKey());
        UeberCertificateDTO secondCert = or.createUeberCertificate(owner.getKey());
        assertNotSame(firstCert.getId(), secondCert.getId());
    }

    @Test
    public void certificateGenerationRaisesExceptionIfOwnerNotFound() {
        when(this.principalProvider.get()).thenReturn(principal);
        assertThrows(NotFoundException.class, () ->
            or.createUeberCertificate("non-existant")
        );
    }

    @Test
    public void certificateRetrievalRaisesExceptionIfOwnerNotFound() {
        when(this.principalProvider.get()).thenReturn(principal);
        assertThrows(NotFoundException.class, () ->
            or.getUeberCertificate("non-existant")
        );
    }

    @Test
    public void certificateRetrievalRaisesExceptionIfNoCertificateWasGenerated() {
        // verify that owner under test doesn't have a certificate
        Owner anotherOwner = ownerCurator.create(new Owner()
            .setKey(OWNER_NAME + "1")
            .setDisplayName(OWNER_NAME + "1"));

        when(this.principalProvider.get()).thenReturn(principal);
        assertThrows(NotFoundException.class, () -> or.getUeberCertificate(anotherOwner.getKey()));
    }

    @Test
    public void certificateRetrievalReturnsCert() {
        when(this.principalProvider.get()).thenReturn(principal);

        UeberCertificateDTO generated = or.createUeberCertificate(owner.getKey());
        assertNotNull(generated);

        UeberCertificateDTO retrieved = or.getUeberCertificate(owner.getKey());
        assertNotNull(retrieved);

        assertEquals(generated, retrieved);
    }

}
