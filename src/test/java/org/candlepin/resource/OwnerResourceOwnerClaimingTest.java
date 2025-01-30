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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.async.JobException;
import org.candlepin.async.JobManager;
import org.candlepin.async.tasks.ConsumerMigrationJob;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.config.Configuration;
import org.candlepin.config.TestConfig;
import org.candlepin.controller.ConsumerManager;
import org.candlepin.controller.ContentAccessManager;
import org.candlepin.controller.ManifestManager;
import org.candlepin.controller.OwnerManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.PoolService;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.ClaimantOwner;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.ExporterMetadataCurator;
import org.candlepin.model.ImportRecordCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerInfoCurator;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.UeberCertificateCurator;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.paging.PagingUtilFactory;
import org.candlepin.pki.certs.UeberCertificateGenerator;
import org.candlepin.resource.util.CalculatedAttributesUtil;
import org.candlepin.resource.util.ConsumerTypeValidator;
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.service.OwnerServiceAdapter;
import org.candlepin.util.ContentOverrideValidator;
import org.candlepin.util.ServiceLevelValidator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xnap.commons.i18n.I18n;


@ExtendWith(MockitoExtension.class)
public class OwnerResourceOwnerClaimingTest {

    private static final String ORIGIN_KEY = "origin";
    private static final String CLAIMANT_KEY = "claimant";

    @Mock
    private PoolManager poolManager;
    @Mock
    private CalculatedAttributesUtil calculatedAttributesUtil;
    @Mock
    private ContentOverrideValidator contentOverrideValidator;
    @Mock
    private DTOValidator dtoValidator;
    @Mock
    private ContentAccessManager contentAccessManager;
    @Mock
    private PoolService poolService;
    @Mock
    private ActivationKeyCurator activationKeyCurator;
    @Mock
    private ConsumerCurator consumerCurator;
    @Mock
    private ConsumerManager consumerManager;
    @Mock
    private EntitlementCurator entitlementCurator;
    @Mock
    private EnvironmentCurator environmentCurator;
    @Mock
    private ExporterMetadataCurator exportCurator;
    @Mock
    private ImportRecordCurator importRecordCurator;
    @Mock
    private OwnerCurator ownerCurator;
    @Mock
    private OwnerInfoCurator ownerInfoCurator;
    @Mock
    private PoolCurator poolCurator;
    @Mock
    private ProductCurator productCurator;
    @Mock
    private PrincipalProvider principalProvider;
    @Mock
    private UeberCertificateCurator ueberCertCurator;
    @Mock
    private UeberCertificateGenerator ueberCertificateGenerator;
    @Mock
    private EventSink eventSink;
    @Mock
    private EventFactory eventFactory;
    @Mock
    private ManifestManager manifestManager;
    @Mock
    private OwnerManager ownerManager;
    @Mock
    private JobManager jobManager;
    @Mock
    private OwnerServiceAdapter ownerServiceAdapter;
    @Mock
    private ServiceLevelValidator serviceLevelValidator;
    @Mock
    private ConsumerTypeValidator consumerTypeValidator;
    @Mock
    private I18n i18n;
    @Mock
    private ModelTranslator modelTranslator;
    @Mock
    private PagingUtilFactory pagingUtilFactory;

    private Configuration config;

    @BeforeEach
    public void setUp() {
        this.config = TestConfig.defaults();
    }

    private OwnerResource buildOwnerResource() {
        return new OwnerResource(this.ownerCurator, this.activationKeyCurator,
            this.consumerCurator, this.consumerManager, this.i18n, this.eventSink, this.eventFactory,
            this.contentAccessManager, this.manifestManager, this.poolManager, this.poolService,
            this.poolCurator, this.ownerManager, this.exportCurator, this.ownerInfoCurator,
            this.importRecordCurator, this.entitlementCurator, this.ueberCertCurator,
            this.ueberCertificateGenerator, this.environmentCurator, this.calculatedAttributesUtil,
            this.contentOverrideValidator, this.serviceLevelValidator, this.ownerServiceAdapter, this.config,
            this.consumerTypeValidator, this.productCurator, this.modelTranslator, this.jobManager,
            this.dtoValidator, this.principalProvider, this.pagingUtilFactory);
    }

    @Test
    public void claimingShouldScheduleMigrationJob() throws JobException {
        when(this.ownerCurator.getByKey(ORIGIN_KEY)).thenReturn(createAnonOwner(ORIGIN_KEY));
        when(this.ownerCurator.getByKey(CLAIMANT_KEY)).thenReturn(createOwner(CLAIMANT_KEY));
        OwnerResource resource = buildOwnerResource();

        resource.claim(ORIGIN_KEY, createClaimant());

        verify(this.jobManager).queueJob(any(ConsumerMigrationJob.ConsumerMigrationJobConfig.class));
    }

    @Test
    public void claimingShouldMarkOwnerClaimed() {
        Owner originOwner = createAnonOwner(ORIGIN_KEY);
        Owner claimantOwner = createOwner(CLAIMANT_KEY);
        when(this.ownerCurator.getByKey(ORIGIN_KEY)).thenReturn(originOwner);
        when(this.ownerCurator.getByKey(CLAIMANT_KEY)).thenReturn(claimantOwner);
        OwnerResource resource = buildOwnerResource();

        resource.claim(ORIGIN_KEY, createClaimant());

        assertThat(originOwner)
            .returns(true, Owner::getClaimed)
            .returns(claimantOwner.getKey(), Owner::getClaimantOwner);
    }

    @Test
    public void onceClaimedClaimantOwnerHasToMatch() {
        Owner originOwner = createAnonOwner(ORIGIN_KEY)
            .setClaimed(true).setClaimantOwner("other_claimant");
        Owner claimantOwner = createOwner(CLAIMANT_KEY);
        when(this.ownerCurator.getByKey(ORIGIN_KEY)).thenReturn(originOwner);
        when(this.ownerCurator.getByKey(CLAIMANT_KEY)).thenReturn(claimantOwner);
        OwnerResource resource = buildOwnerResource();

        assertThatThrownBy(() -> resource.claim(ORIGIN_KEY, createClaimant()))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    public void originHasToBeAnonymous() {
        Owner originOwner = createOwner(ORIGIN_KEY);
        when(this.ownerCurator.getByKey(ORIGIN_KEY)).thenReturn(originOwner);
        OwnerResource resource = buildOwnerResource();

        assertThatThrownBy(() -> resource.claim(ORIGIN_KEY, createClaimant()))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    public void claimantCannotBeAnonymous() {
        Owner originOwner = createAnonOwner(ORIGIN_KEY);
        Owner claimantOwner = createAnonOwner(CLAIMANT_KEY);
        when(this.ownerCurator.getByKey(ORIGIN_KEY)).thenReturn(originOwner);
        when(this.ownerCurator.getByKey(CLAIMANT_KEY)).thenReturn(claimantOwner);
        OwnerResource resource = buildOwnerResource();

        assertThatThrownBy(() -> resource.claim(ORIGIN_KEY, createClaimant()))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    public void testClaimWhenClaimantOwnerExistOnlyUpstream() {
        Owner originOwner = createAnonOwner(ORIGIN_KEY);
        when(this.ownerCurator.getByKey(ORIGIN_KEY)).thenReturn(originOwner);
        when(this.ownerCurator.getByKey(CLAIMANT_KEY)).thenReturn(null);
        when(this.ownerServiceAdapter.isOwnerKeyValidForCreation(CLAIMANT_KEY)).thenReturn(true);
        OwnerResource resource = buildOwnerResource();

        resource.claim(ORIGIN_KEY, createClaimant());

        verify(ownerCurator, times(1)).create(any(Owner.class));
    }

    @Test
    public void testClaimWhenClaimantOwnerDoesNotExistUpstreamOrCandlepin() {
        Owner originOwner = createAnonOwner(ORIGIN_KEY);
        when(this.ownerCurator.getByKey(ORIGIN_KEY)).thenReturn(originOwner);
        when(this.ownerCurator.getByKey(CLAIMANT_KEY)).thenReturn(null);
        when(this.ownerServiceAdapter.isOwnerKeyValidForCreation(CLAIMANT_KEY)).thenReturn(false);
        OwnerResource resource = buildOwnerResource();

        assertThatThrownBy(() -> resource.claim(ORIGIN_KEY, createClaimant()))
            .isInstanceOf(BadRequestException.class);
    }

    private ClaimantOwner createClaimant() {
        return new ClaimantOwner().claimantOwnerKey(CLAIMANT_KEY);
    }

    private Owner createOwner(String key) {
        return new Owner().setKey(key);
    }

    private Owner createAnonOwner(String key) {
        return new Owner().setKey(key).setAnonymous(true);
    }

}
