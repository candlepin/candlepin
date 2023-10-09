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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.async.JobManager;
import org.candlepin.controller.ContentAccessManager;
import org.candlepin.controller.EntitlementCertificateGenerator;
import org.candlepin.controller.PoolService;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.SimpleModelTranslator;
import org.candlepin.dto.api.server.v1.ConsumerDTO;
import org.candlepin.dto.api.server.v1.ContentDTO;
import org.candlepin.dto.api.server.v1.EnvironmentDTO;
import org.candlepin.dto.api.server.v1.NestedOwnerDTO;
import org.candlepin.dto.api.v1.ContentTranslator;
import org.candlepin.dto.api.v1.EnvironmentTranslator;
import org.candlepin.dto.api.v1.NestedOwnerTranslator;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Content;
import org.candlepin.model.ContentAccessCertificate;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContentCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.IdentityCertificateCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.test.TestUtil;
import org.candlepin.util.RdbmsExceptionTranslator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;



@ExtendWith(MockitoExtension.class)
class EnvironmentResourceTest {

    private static final String ENV_ID_1 = "env_id_1";
    private static final String BAD_ID = "bad_id";

    @Mock
    private EnvironmentCurator envCurator;
    @Mock
    private EnvironmentContentCurator envContentCurator;
    @Mock
    private ConsumerResource consumerResource;
    @Mock
    private PoolService poolService;
    @Mock
    private ConsumerCurator consumerCurator;
    @Mock
    private OwnerContentCurator ownerContentCurator;
    @Mock
    private RdbmsExceptionTranslator rdbmsExceptionTranslator;
    @Mock
    private JobManager jobManager;
    @Mock
    private DTOValidator validator;
    @Mock
    private ContentAccessManager contentAccessManager;
    @Mock
    private CertificateSerialCurator certificateSerialCurator;
    @Mock
    private IdentityCertificateCurator identityCertificateCurator;
    @Mock
    private ContentAccessCertificateCurator contentAccessCertificateCurator;
    @Mock
    private EntitlementCurator entitlementCurator;
    @Mock
    private EntitlementCertificateGenerator entCertGenerator;
    private I18n i18n;
    private ModelTranslator translator;

    private EnvironmentResource environmentResource;

    private Owner owner;
    private Environment environment1;

    @BeforeEach
    void setUp() {
        this.i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        this.translator = new SimpleModelTranslator();
        this.translator.registerTranslator(new EnvironmentTranslator(),
            Environment.class, EnvironmentDTO.class);
        this.translator.registerTranslator(new ContentTranslator(), Content.class, ContentDTO.class);
        this.translator.registerTranslator(new NestedOwnerTranslator(), Owner.class, NestedOwnerDTO.class);

        this.environmentResource = new EnvironmentResource(
            this.envCurator,
            this.i18n,
            this.envContentCurator,
            this.consumerResource,
            this.poolService,
            this.consumerCurator,
            this.ownerContentCurator,
            this.rdbmsExceptionTranslator,
            this.translator,
            this.jobManager,
            this.validator,
            this.contentAccessManager,
            this.certificateSerialCurator,
            this.identityCertificateCurator,
            this.contentAccessCertificateCurator,
            this.entitlementCurator,
            this.entCertGenerator);

        this.owner = TestUtil.createOwner("owner1");
        this.environment1 = createEnvironment(owner, ENV_ID_1);
    }

    @Test
    void environmentNotFound() {
        assertThrows(NotFoundException.class, () -> this.environmentResource.getEnvironment(BAD_ID));
    }

    @Test
    void environmentFound() {
        when(this.envCurator.get(anyString())).thenReturn(this.environment1);

        EnvironmentDTO environment = this.environmentResource.getEnvironment(ENV_ID_1);

        assertNotNull(environment);
    }

    @Test
    void nothingToDelete() {
        assertThrows(NotFoundException.class, () -> this.environmentResource.deleteEnvironment(BAD_ID));
    }

    @Test
    void canDeleteEmptyEnvironment() {
        when(this.envCurator.get(anyString())).thenReturn(this.environment1);
        List<Consumer> mockedQuery = mockedQueryOf().list();
        when(this.envCurator.getEnvironmentConsumers(this.environment1))
            .thenReturn(mockedQuery);

        this.environmentResource.deleteEnvironment(BAD_ID);

        verifyNoInteractions(this.poolService);
        verifyNoInteractions(this.consumerCurator);
        verifyNoInteractions(this.certificateSerialCurator);
        verifyNoInteractions(this.identityCertificateCurator);
        verifyNoInteractions(this.contentAccessCertificateCurator);
        verify(this.envCurator).delete(this.environment1);
    }

    @Test
    void shouldCleanUpAfterDeletingEnvironment() {
        Consumer consumer1 = createConsumer(this.environment1);
        Consumer consumer2 = createConsumer(this.environment1);
        consumer2.setIdCert(null);
        consumer2.setContentAccessCert(null);
        when(this.envCurator.get(anyString())).thenReturn(this.environment1);
        List<Consumer> mockedQuery = mockedQueryOf(consumer1, consumer2).list();
        when(this.envCurator.getEnvironmentConsumers(this.environment1))
            .thenReturn(mockedQuery);

        this.environmentResource.deleteEnvironment(ENV_ID_1);

        verify(this.identityCertificateCurator).deleteByIds(anyList());
        verify(this.contentAccessCertificateCurator).deleteByIds(anyList());
        verify(this.certificateSerialCurator).revokeByIds(anyList());
        verify(this.envCurator).delete(this.environment1);
    }

    @Test
    void onlyConsumersWithTheirLastEnvRemovedShouldBeCleanedUp() {
        Consumer consumer1 = createConsumer(this.environment1);
        Consumer consumer2 = createConsumer(this.environment1);
        Environment environment2 = createEnvironment(owner, "env_id_2");
        consumer2.setIdCert(null);
        consumer2.setContentAccessCert(null);
        consumer2.addEnvironment(environment2);
        when(this.envCurator.get(anyString())).thenReturn(this.environment1);
        List<Consumer> mockedQuery = mockedQueryOf(consumer1, consumer2).list();
        when(this.envCurator.getEnvironmentConsumers(this.environment1))
            .thenReturn(mockedQuery);

        this.environmentResource.deleteEnvironment(ENV_ID_1);

        verify(this.consumerCurator).delete(consumer1);
        verify(this.contentAccessManager).removeContentAccessCert(consumer2);
    }

    @Test
    void shouldThrowExceptionForNonExistentEnvIDs() {
        ConsumerDTO dto = new ConsumerDTO();

        assertThrows(NotFoundException.class, () -> this.environmentResource
            .createConsumerInEnvironment("randomEnvId1, randomEnvId2",
                dto, "userName", null));
    }

    @Test
    void shouldThrowExceptionIfAnyOfEnvIdsDoesNotBelongToSameOwner() {
        ConsumerDTO dto = new ConsumerDTO();
        String envIds = "env1,env2,env3";

        Owner owner1 = TestUtil.createOwner("owner1");
        Owner owner2 = TestUtil.createOwner("owner2");

        Environment env1 = createEnvironment(owner1, "env1");
        Environment env2 = createEnvironment(owner2, "env2");
        Environment env3 = createEnvironment(owner1, "env3");
        when(this.envCurator.get(anyString())).thenReturn(env2, env1, env3);

        assertThrows(BadRequestException.class, () -> this.environmentResource
            .createConsumerInEnvironment(envIds, dto, "userName", null));
    }

    @Test
    void shouldCreateConsumerInMultipleEnvironment() {
        String envIds = "env1,env3,env2";
        ConsumerDTO dto = new ConsumerDTO().name("testConsumer");
        Environment env1 = createEnvironment(this.owner, "env1");
        Environment env2 = createEnvironment(this.owner, "env2");
        Environment env3 = createEnvironment(this.owner, "env3");

        when(this.envCurator.get(anyString())).thenReturn(env1, env3, env2);
        when(this.consumerResource.createConsumer(any(), any(), any(), any(), any())).thenReturn(dto);

        dto = this.environmentResource.createConsumerInEnvironment(envIds, dto, "userName", null);

        assertNotNull(dto.getEnvironments());
        assertEquals(3, dto.getEnvironments().size());
        assertEquals("env1", dto.getEnvironments().get(0).getId());
        assertEquals("env3", dto.getEnvironments().get(1).getId());
        assertEquals("env2", dto.getEnvironments().get(2).getId());
    }

    @SuppressWarnings("unchecked")
    private CandlepinQuery<Consumer> mockedQueryOf(Consumer... items) {
        CandlepinQuery<Consumer> candlepinQuery = mock(CandlepinQuery.class);
        when(candlepinQuery.list()).thenReturn(Arrays.asList(items));
        return candlepinQuery;
    }

    private Environment createEnvironment(Owner owner, String id) {
        return new Environment(id, "Environment " + id, owner);
    }

    private Consumer createConsumer(Environment environment) {
        ConsumerType cType = new ConsumerType(ConsumerType.ConsumerTypeEnum.CANDLEPIN);
        cType.setId("ctype1");

        return new Consumer()
            .setName("c1")
            .setUsername("u1")
            .setOwner(this.owner)
            .setType(cType)
            .setIdCert(TestUtil.createIdCert())
            .setContentAccessCert(createContentAccessCert())
            .addEnvironment(environment);
    }

    private ContentAccessCertificate createContentAccessCert() {
        ContentAccessCertificate certificate = new ContentAccessCertificate();
        certificate.setKey("crt_key");
        certificate.setSerial(new CertificateSerial());
        certificate.setCert("cert_1");
        certificate.setContent("content_1");
        return certificate;
    }
}
