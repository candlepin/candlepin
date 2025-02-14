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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.async.JobManager;
import org.candlepin.controller.ContentAccessManager;
import org.candlepin.controller.EntitlementCertificateService;
import org.candlepin.controller.PoolService;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.SimpleModelTranslator;
import org.candlepin.dto.api.server.v1.ConsumerDTO;
import org.candlepin.dto.api.server.v1.ContentDTO;
import org.candlepin.dto.api.server.v1.ContentOverrideDTO;
import org.candlepin.dto.api.server.v1.ContentToPromoteDTO;
import org.candlepin.dto.api.server.v1.EnvironmentContentDTO;
import org.candlepin.dto.api.server.v1.EnvironmentDTO;
import org.candlepin.dto.api.server.v1.NestedOwnerDTO;
import org.candlepin.dto.api.v1.ContentTranslator;
import org.candlepin.dto.api.v1.EnvironmentTranslator;
import org.candlepin.dto.api.v1.NestedOwnerTranslator;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Content;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.EnvironmentContentCurator;
import org.candlepin.model.EnvironmentContentOverride;
import org.candlepin.model.EnvironmentContentOverrideCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.IdentityCertificateCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.SCACertificate;
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ContentOverrideValidator;
import org.candlepin.util.RdbmsExceptionTranslator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.lang.reflect.InvocationTargetException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;



@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
    private ContentCurator contentCurator;
    @Mock
    private RdbmsExceptionTranslator rdbmsExceptionTranslator;
    @Mock
    private JobManager jobManager;
    @Mock
    private DTOValidator dtoValidator;
    @Mock
    private ContentOverrideValidator contentOverrideValidator;
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
    private EntitlementCertificateService entCertService;
    @Mock
    private EnvironmentContentOverrideCurator envContentOverrideCurator;

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
            this.contentCurator,
            this.rdbmsExceptionTranslator,
            this.translator,
            this.jobManager,
            this.dtoValidator,
            this.contentOverrideValidator,
            this.contentAccessManager,
            this.certificateSerialCurator,
            this.identityCertificateCurator,
            this.contentAccessCertificateCurator,
            this.entitlementCurator,
            this.entCertService,
            this.envContentOverrideCurator);

        // TODO: Stop doing this! Globally shared data means every test in the suite has to account
        // for this or risk counts/queries not returning precise results! Just create the objects in
        // the test as necessary!
        this.owner = TestUtil.createOwner("owner1");
        this.environment1 = createEnvironment(owner, ENV_ID_1);

        doAnswer(returnsFirstArg()).when(this.envCurator).merge(any(Environment.class));
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

    private Environment createMockedEnvironment() {
        Date now = new Date();

        Environment environment = new Environment()
            .setId(TestUtil.randomString(16, TestUtil.CHARSET_ALPHANUMERIC))
            .setCreated(now)
            .setUpdated(now);

        doReturn(environment).when(this.envCurator).get(environment.getId());

        return environment;
    }

    @Test
    public void testUpdateEnvironmentCanUpdateName() {
        Environment environment = this.createMockedEnvironment()
            .setName("initial name");

        EnvironmentDTO update = new EnvironmentDTO()
            .name("updated name");

        EnvironmentDTO result = this.environmentResource.updateEnvironment(environment.getId(), update);

        assertEquals(update.getName(), result.getName());
        assertEquals(update.getName(), environment.getName());
    }

    @Test
    public void testUpdateEnvironmentRejectsEmptyName() {
        String initialValue = "initial name";

        Environment environment = this.createMockedEnvironment()
            .setName(initialValue);

        EnvironmentDTO update = new EnvironmentDTO()
            .name("");

        assertThrows(BadRequestException.class, () ->
            this.environmentResource.updateEnvironment(environment.getId(), update));

        assertEquals(initialValue, environment.getName());
    }

    @Test
    public void testUpdateEnvironmentRejectsLongName() {
        String initialValue = "initial name";

        Environment environment = this.createMockedEnvironment()
            .setName(initialValue);

        EnvironmentDTO update = new EnvironmentDTO()
            .name("o".repeat(Environment.NAME_MAX_LENGTH + 1));

        assertThrows(BadRequestException.class, () ->
            this.environmentResource.updateEnvironment(environment.getId(), update));

        assertEquals(initialValue, environment.getName());
    }

    @Test
    public void testUpdateEnvironmentCanUpdateType() {
        Environment environment = this.createMockedEnvironment()
            .setType("initial type");

        EnvironmentDTO update = new EnvironmentDTO()
            .type("updated type");

        EnvironmentDTO result = this.environmentResource.updateEnvironment(environment.getId(), update);

        assertEquals(update.getType(), result.getType());
        assertEquals(update.getType(), environment.getType());
    }

    @Test
    public void testUpdateEnvironmentCanClearType() {
        Environment environment = this.createMockedEnvironment()
            .setType("initial type");

        EnvironmentDTO update = new EnvironmentDTO()
            .type("");

        EnvironmentDTO result = this.environmentResource.updateEnvironment(environment.getId(), update);

        assertNull(result.getType());
        assertNull(environment.getType());
    }

    @Test
    public void testUpdateEnvironmentRejectsLongType() {
        String initialValue = "initial type";

        Environment environment = this.createMockedEnvironment()
            .setType(initialValue);

        EnvironmentDTO update = new EnvironmentDTO()
            .type("o".repeat(Environment.TYPE_MAX_LENGTH + 1));

        assertThrows(BadRequestException.class, () ->
            this.environmentResource.updateEnvironment(environment.getId(), update));

        assertEquals(initialValue, environment.getType());
    }

    @Test
    public void testUpdateEnvironmentCanUpdateDescription() {
        Environment environment = this.createMockedEnvironment()
            .setDescription("initial description");

        EnvironmentDTO update = new EnvironmentDTO()
            .description("updated description");

        EnvironmentDTO result = this.environmentResource.updateEnvironment(environment.getId(), update);

        assertEquals(update.getDescription(), result.getDescription());
        assertEquals(update.getDescription(), environment.getDescription());
    }

    @Test
    public void testUpdateEnvironmentCanClearDescription() {
        Environment environment = this.createMockedEnvironment()
            .setDescription("initial description");

        EnvironmentDTO update = new EnvironmentDTO()
            .description("");

        EnvironmentDTO result = this.environmentResource.updateEnvironment(environment.getId(), update);

        assertNull(result.getDescription());
        assertNull(environment.getDescription());
    }

    @Test
    public void testUpdateEnvironmentRejectsLongDescription() {
        String initialValue = "initial description";

        Environment environment = this.createMockedEnvironment()
            .setDescription(initialValue);

        EnvironmentDTO update = new EnvironmentDTO()
            .description("o".repeat(Environment.DESCRIPTION_MAX_LENGTH + 1));

        assertThrows(BadRequestException.class, () ->
            this.environmentResource.updateEnvironment(environment.getId(), update));

        assertEquals(initialValue, environment.getDescription());
    }

    @Test
    public void testUpdateEnvironmentCanUpdateContentPrefix() {
        Environment environment = this.createMockedEnvironment()
            .setContentPrefix("initial contentprefix");

        EnvironmentDTO update = new EnvironmentDTO()
            .contentPrefix("updated contentprefix");

        EnvironmentDTO result = this.environmentResource.updateEnvironment(environment.getId(), update);

        assertEquals(update.getContentPrefix(), result.getContentPrefix());
        assertEquals(update.getContentPrefix(), environment.getContentPrefix());
    }

    @Test
    public void testUpdateEnvironmentCanClearContentPrefix() {
        Environment environment = this.createMockedEnvironment()
            .setContentPrefix("initial contentprefix");

        EnvironmentDTO update = new EnvironmentDTO()
            .contentPrefix("");

        EnvironmentDTO result = this.environmentResource.updateEnvironment(environment.getId(), update);

        assertNull(result.getContentPrefix());
        assertNull(environment.getContentPrefix());
    }

    @Test
    public void testUpdateEnvironmentRejectsLongContentPrefix() {
        String initialValue = "initial contentprefix";

        Environment environment = this.createMockedEnvironment()
            .setContentPrefix(initialValue);

        EnvironmentDTO update = new EnvironmentDTO()
            .contentPrefix("o".repeat(Environment.CONTENT_PREFIX_MAX_LENGTH + 1));

        assertThrows(BadRequestException.class, () ->
            this.environmentResource.updateEnvironment(environment.getId(), update));

        assertEquals(initialValue, environment.getContentPrefix());
    }

    /**
     * These tests verify we don't update fields that either should not be updated (ID, owner) or
     * need special juggling for cert and content access (env content)
     */
    @Test
    public void testUpdateEnvironmentIgnoresId() {
        Environment environment = this.createMockedEnvironment();

        String initialValue = environment.getId();

        EnvironmentDTO update = new EnvironmentDTO()
            .id("new id");

        EnvironmentDTO result = this.environmentResource.updateEnvironment(environment.getId(), update);

        assertEquals(initialValue, environment.getId());
    }

    @Test
    public void testUpdateEnvironmentIgnoresCreatedTimestamp() {
        Environment environment = this.createMockedEnvironment();

        Date initialValue = environment.getCreated();

        EnvironmentDTO update = new EnvironmentDTO()
            .created(OffsetDateTime.now().minusDays(5));

        EnvironmentDTO result = this.environmentResource.updateEnvironment(environment.getId(), update);

        assertEquals(initialValue, environment.getCreated());
    }

    @Test
    public void testUpdateEnvironmentIgnoresUpdatedTimestamp() {
        Environment environment = this.createMockedEnvironment();

        Date initialValue = environment.getUpdated();

        EnvironmentDTO update = new EnvironmentDTO()
            .updated(OffsetDateTime.now().minusDays(5));

        EnvironmentDTO result = this.environmentResource.updateEnvironment(environment.getId(), update);

        assertEquals(initialValue, environment.getUpdated());
    }

    @Test
    public void testUpdateEnvironmentIgnoresOwner() {
        Owner owner = new Owner()
            .setId("owner id")
            .setKey("owner key");

        Environment environment = this.createMockedEnvironment()
            .setOwner(owner);

        String initialValue = environment.getId();

        NestedOwnerDTO ownerUpdate = new NestedOwnerDTO()
            .id("updated id")
            .key("updated key");

        EnvironmentDTO update = new EnvironmentDTO()
            .owner(ownerUpdate);

        EnvironmentDTO result = this.environmentResource.updateEnvironment(environment.getId(), update);

        assertEquals(owner.getId(), environment.getOwnerId());
        assertEquals(owner.getKey(), environment.getOwnerKey());
    }

    @Test
    public void testUpdateEnvironmentIgnoresEnvironmentContent() {
        EnvironmentContent ec1 = new EnvironmentContent()
            .setContentId("content1")
            .setEnabled(true);

        Environment environment = this.createMockedEnvironment()
            .addEnvironmentContent(ec1);

        EnvironmentContentDTO envContentUpdate1 = new EnvironmentContentDTO()
            .contentId("content1")
            .enabled(false);

        EnvironmentContentDTO envContentUpdate2 = new EnvironmentContentDTO()
            .contentId("content2")
            .enabled(true);

        EnvironmentDTO update = new EnvironmentDTO()
            .addEnvironmentContentItem(envContentUpdate1)
            .addEnvironmentContentItem(envContentUpdate2);

        EnvironmentDTO result = this.environmentResource.updateEnvironment(environment.getId(), update);

        Set<EnvironmentContent> environmentContent = environment.getEnvironmentContent();
        assertNotNull(environmentContent);
        assertEquals(1, environmentContent.size());

        EnvironmentContent elem1 = environmentContent.iterator().next();
        assertNotNull(elem1);
        assertEquals(ec1.getContentId(), elem1.getContentId());
        assertEquals(ec1.getEnabled(), elem1.getEnabled());
    }

    /**
     * Test to verify that we don't do partial updates in the event that we have multiple fields to
     * update, some valid and some not.
     */
    @ParameterizedTest
    @ValueSource(strings = { "name", "type", "description", "contentPrefix" })
    public void testUpdateEnvironmentIsAtomic(String invalidFieldName) {
        String initialName = "initial name";
        String initialType = "initial type";
        String initialDescription = "initial description";
        String initialContentPrefix = "initial prefix";

        Environment environment = this.createMockedEnvironment()
            .setName(initialName)
            .setType(initialType)
            .setDescription(initialDescription)
            .setContentPrefix(initialContentPrefix);

        EnvironmentDTO update = new EnvironmentDTO()
            .name("updated name")
            .type("updated type")
            .description("updated description")
            .contentPrefix("updated prefix");

        try {
            // Make the designated field an invalid field with a ridiculously long value
            String invalidFieldValue = "void".repeat(512);

            EnvironmentDTO.class.getMethod(invalidFieldName, String.class)
                .invoke(update, invalidFieldValue);
        }
        catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            // Rethrow anything that happens in here
            throw new RuntimeException(e);
        }

        assertThrows(BadRequestException.class, () ->
            this.environmentResource.updateEnvironment(environment.getId(), update));

        assertEquals(initialName, environment.getName());
        assertEquals(initialType, environment.getType());
        assertEquals(initialDescription, environment.getDescription());
        assertEquals(initialContentPrefix, environment.getContentPrefix());
    }

    @Test
    void nothingToDelete() {
        assertThrows(NotFoundException.class, () -> this.environmentResource.deleteEnvironment(BAD_ID, true));
    }

    @Test
    void canDeleteEmptyEnvironment() {
        when(this.envCurator.get(anyString())).thenReturn(this.environment1);
        when(this.envCurator.getEnvironmentConsumers(this.environment1))
            .thenReturn(List.of());

        this.environmentResource.deleteEnvironment(BAD_ID, true);

        verifyNoInteractions(this.poolService);
        verifyNoInteractions(this.consumerCurator);
        verifyNoInteractions(this.certificateSerialCurator);
        verifyNoInteractions(this.identityCertificateCurator);
        verifyNoInteractions(this.contentAccessCertificateCurator);
        verify(this.envCurator).delete(this.environment1);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = { false })
    void shouldCleanUpAfterDeletingEnvironment(Boolean retainConsumers) {
        Consumer consumer1 = createConsumer(this.environment1);
        Consumer consumer2 = createConsumer(this.environment1);
        consumer2.setIdCert(null);
        consumer2.setContentAccessCert(null);
        when(this.envCurator.get(anyString())).thenReturn(this.environment1);
        when(this.envCurator.getEnvironmentConsumers(this.environment1))
            .thenReturn(List.of(consumer1, consumer2));

        this.environmentResource.deleteEnvironment(ENV_ID_1, retainConsumers);

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
        when(this.envCurator.getEnvironmentConsumers(this.environment1))
            .thenReturn(List.of(consumer1, consumer2));

        this.environmentResource.deleteEnvironment(ENV_ID_1, false);

        verify(this.consumerCurator).delete(consumer1);
        verify(this.contentAccessManager).removeContentAccessCert(consumer2);
    }

    @Test
    void shouldRemoveConsumersFromEnvironmentWithoutDeletionWhenRetainFlagIsSet() {
        Consumer consumer1 = createConsumer(this.environment1);
        Consumer consumer2 = createConsumer(this.environment1);
        Environment environment2 = createEnvironment(owner, "env_id_2");
        consumer2.setIdCert(null);
        consumer2.setContentAccessCert(null);
        consumer2.addEnvironment(environment2);

        when(this.envCurator.get(anyString())).thenReturn(this.environment1);
        when(this.envCurator.getEnvironmentConsumers(this.environment1))
            .thenReturn(List.of(consumer1, consumer2));

        this.environmentResource.deleteEnvironment(ENV_ID_1, true);

        verify(this.consumerCurator, never()).delete(any(Consumer.class));
        verify(this.contentAccessManager, times(2)).removeContentAccessCert(consumer2);
        verify(this.envCurator).delete(this.environment1);
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

    @Test
    public void shouldUpdateLastContentFieldOnContentPromote() {
        Date beforePromote = minusTwoSeconds();
        Environment env = this.createMockedEnvironment()
            .setOwner(this.owner)
            .setLastContentUpdate(beforePromote);
        ContentToPromoteDTO dto = new ContentToPromoteDTO()
            .contentId("con-1");
        Content content = new Content("con-1");

        when(contentCurator.resolveContentId(anyString(), anyString())).thenReturn(content);

        this.environmentResource.promoteContent(env.getId(), List.of(dto), true);
        Date afterPromote = env.getLastContentUpdate();

        assertThat(beforePromote)
            .isBefore(afterPromote);
    }

    @Test
    public void shouldUpdateLastContentFieldOnContentDemote() {
        Content content = new Content("con-1");
        EnvironmentContent enContent = new EnvironmentContent()
            .setContent(content);
        Date beforeDemote = minusTwoSeconds();
        Environment env = this.createMockedEnvironment()
            .setOwner(this.owner)
            .setEnvironmentContent(Set.of(enContent))
            .setLastContentUpdate(beforeDemote);

        when(envContentCurator.getByEnvironmentAndContent(any(), anyString())).thenReturn(enContent);

        this.environmentResource.demoteContent(env.getId(), List.of(content.getId()), true);
        Date afterDemote = env.getLastContentUpdate();

        assertThat(beforeDemote)
            .isBefore(afterDemote);
    }

    @Test
    public void shouldUpdateLastContentFieldOnContentPrefixChange() {
        Date beforeUpdate = minusTwoSeconds();
        Environment env = this.createMockedEnvironment()
            .setOwner(this.owner)
            .setContentPrefix("contentPrefix")
            .setLastContentUpdate(beforeUpdate);
        EnvironmentDTO dto = new EnvironmentDTO()
            .contentPrefix("newContentUrl");

        this.environmentResource.updateEnvironment(env.getId(), dto);
        Date afterUpdate = env.getLastContentUpdate();

        assertThat(env.getContentPrefix())
            .isEqualTo("newContentUrl");
        assertThat(beforeUpdate)
            .isBefore(afterUpdate);
    }

    private Date minusTwoSeconds() {
        return new Date(System.currentTimeMillis() - 2000);
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

    private SCACertificate createContentAccessCert() {
        SCACertificate certificate = new SCACertificate();
        certificate.setKey("crt_key");
        certificate.setSerial(new CertificateSerial());
        certificate.setCert("cert_1");
        return certificate;
    }

    @Nested
    @DisplayName("Environment Content Overrides Tests")
    public class EnvironmentContentOverridesTests extends DatabaseTestFixture {
        private EnvironmentResource buildResource() {
            return this.injector.getInstance(EnvironmentResource.class);
        }

        @Test
        public void testPutEnvironmentContentOverrides() {
            Owner owner = this.createOwner(TestUtil.randomString());
            Environment e1 = this.createEnvironment(owner);
            Environment e2 = this.createEnvironment(owner);

            List<ContentOverrideDTO> overridesToAdd = new ArrayList<>();

            for (int idx = 1; idx <= 3; ++idx) {
                String name = String.format("existing-e1-co-%d", idx);
                String label = String.format("existing-e1-label-%d", idx);

                // Create and persist some initial environment 1 content overrides
                EnvironmentContentOverride contentOverride = new EnvironmentContentOverride()
                    .setEnvironment(e1)
                    .setName(name)
                    .setContentLabel(label)
                    .setValue(TestUtil.randomString());

                this.environmentContentOverrideCurator.create(contentOverride);

                // Create and persist some initial environment 2 content overrides
                EnvironmentContentOverride e2ContentOverride = new EnvironmentContentOverride()
                    .setEnvironment(e2)
                    .setName(String.format("existing-e2-co-%d", idx))
                    .setContentLabel(String.format("existing-e2-label-%d", idx))
                    .setValue(TestUtil.randomString());

                this.environmentContentOverrideCurator.create(e2ContentOverride);

                // Add a content override to update the persisted EnvironmentContentOverride
                ContentOverrideDTO contentOverrideUpdate = new ContentOverrideDTO();
                contentOverrideUpdate.setName(name);
                contentOverrideUpdate.setContentLabel(label);
                contentOverrideUpdate.setValue(TestUtil.randomString(label + "-modified-"));

                overridesToAdd.add(contentOverrideUpdate);

                // Add an unpersisted net new content overrides
                ContentOverrideDTO netNewContentOverride = new ContentOverrideDTO();
                netNewContentOverride.setName(String.format("new-co-%d", idx));
                netNewContentOverride.setContentLabel(String.format("new-label-%d", idx));
                netNewContentOverride.setValue(TestUtil.randomString());

                overridesToAdd.add(netNewContentOverride);
            }

            Stream<ContentOverrideDTO> actual = this.buildResource()
                .putEnvironmentContentOverrides(e1.getId(), overridesToAdd);

            assertThat(actual)
                .isNotNull()
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
                .containsExactlyInAnyOrderElementsOf(overridesToAdd);
        }

        @Test
        public void testPutEnvironmentContentOverridesWithSameNameAndDifferentValues() {
            Owner owner = this.createOwner(TestUtil.randomString());
            Environment environment = this.createEnvironment(owner);

            String expectedLabel = "label";

            ContentOverrideDTO co1 = new ContentOverrideDTO();
            co1.setName("override");
            co1.setContentLabel(expectedLabel);
            co1.setValue("co1-value");

            ContentOverrideDTO co2 = new ContentOverrideDTO();
            co2.setName("OvErRiDE");
            co2.setContentLabel(expectedLabel);
            co2.setValue("co2-value");

            Stream<ContentOverrideDTO> actual = this.buildResource()
                .putEnvironmentContentOverrides(environment.getId(), List.of(co1, co2));

            assertThat(actual)
                .isNotNull()
                .singleElement()
                .returns(co1.getName(), ContentOverrideDTO::getName)
                .returns(expectedLabel, ContentOverrideDTO::getContentLabel)
                .returns(co2.getValue(), ContentOverrideDTO::getValue);
        }
    }
}
