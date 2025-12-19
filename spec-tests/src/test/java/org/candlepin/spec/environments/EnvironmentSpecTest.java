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
package org.candlepin.spec.environments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.CertificateAssert.assertThatCert;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertConflict;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertGone;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.dto.api.client.v1.ActivationKeyDTO;
import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.CertificateDTO;
import org.candlepin.dto.api.client.v1.CertificateSerialDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.ContentToPromoteDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.EnvironmentContentDTO;
import org.candlepin.dto.api.client.v1.EnvironmentDTO;
import org.candlepin.dto.api.client.v1.NestedOwnerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.data.builder.ActivationKeys;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.Environments;
import org.candlepin.spec.bootstrap.data.builder.Facts;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.CertificateUtil;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.JsonNode;

import java.lang.reflect.InvocationTargetException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


@SpecTest
public class EnvironmentSpecTest {

    private static ApiClient admin;

    private OwnerDTO owner;
    private ApiClient ownerClient;

    @BeforeAll
    public static void beforeAll() {
        admin = ApiClients.admin();
    }

    @BeforeEach
    void setUp() {
        this.owner = admin.owners().createOwner(Owners.random());
        this.ownerClient = ApiClients.basic(UserUtil.createAdminUser(admin, this.owner));
    }

    @Test
    public void shouldPopulateGeneratedFieldsWhenCreatingEnvironments() {
        OffsetDateTime init = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        EnvironmentDTO output = this.ownerClient.owners()
            .createEnvironment(this.owner.getKey(), Environments.random());

        OffsetDateTime post = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        assertThat(output.getId())
            .isNotNull()
            .isNotBlank();

        assertThat(output.getCreated())
            .isNotNull()
            .isAfterOrEqualTo(init)
            .isBeforeOrEqualTo(post);

        assertThat(output.getUpdated())
            .isNotNull()
            .isAfterOrEqualTo(init)
            .isAfterOrEqualTo(output.getCreated())
            .isBeforeOrEqualTo(post);
    }

    @Test
    public void shouldPopulateGeneratedFieldsWhenUpdatingEnvironments() throws Exception {
        EnvironmentDTO entity = this.ownerClient.owners()
            .createEnvironment(this.owner.getKey(), Environments.random());

        Thread.sleep(1100);

        OffsetDateTime init = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        entity.setName(entity.getName() + "-update");
        EnvironmentDTO output = this.ownerClient.environments().updateEnvironment(entity.getId(), entity);

        OffsetDateTime post = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        assertThat(output.getCreated())
            .isNotNull()
            .isEqualTo(entity.getCreated())
            .isBeforeOrEqualTo(init);

        assertThat(output.getUpdated())
            .isNotNull()
            .isAfter(output.getCreated())
            .isAfterOrEqualTo(init)
            .isBeforeOrEqualTo(post);
    }

    @Test
    public void shouldAllowOwnerAdminToCreateEnvironments() {
        EnvironmentDTO expectedEnv = Environments.random();

        EnvironmentDTO env = ownerClient.owners().createEnvironment(owner.getKey(), expectedEnv);
        assertThat(env)
            .isNotNull()
            .returns(expectedEnv.getId(), EnvironmentDTO::getId)
            .extracting(EnvironmentDTO::getOwner)
            .returns(owner.getKey(), NestedOwnerDTO::getKey);

        List<EnvironmentDTO> environments = ownerClient.owners().listEnvironments(owner);
        assertThat(environments)
            .singleElement()
            .returns(expectedEnv.getId(), EnvironmentDTO::getId)
            .returns(expectedEnv.getContentPrefix(), EnvironmentDTO::getContentPrefix);
    }

    @Test
    public void shouldAllowOwnerAdminToDeleteEnvironments() {
        EnvironmentDTO env = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());

        ownerClient.environments().deleteEnvironment(env.getId());

        List<EnvironmentDTO> environments = ownerClient.owners().listEnvironments(owner);
        assertThat(environments)
            .isEmpty();
    }

    @Test
    public void shouldNotAllowForeignAdminToCreateEnvironments() {
        OwnerDTO foreignOwner = admin.owners().createOwner(Owners.random());
        ApiClient foreignClient = ApiClients.basic(UserUtil.createUser(admin, foreignOwner));

        assertNotFound(() -> foreignClient.owners().createEnvironment(owner.getKey(), Environments.random()));
    }

    @Test
    public void shouldNotAllowForeignAdminToAccessEnvironments() {
        EnvironmentDTO env = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        OwnerDTO foreignOwner = admin.owners().createOwner(Owners.random());
        ApiClient foreignClient = ApiClients.basic(UserUtil.createUser(admin, foreignOwner));

        assertNotFound(() -> foreignClient.owners().listEnvironments(owner));
        assertNotFound(() -> foreignClient.environments().getEnvironment(env.getId()));
        assertNotFound(() -> foreignClient.environments().deleteEnvironment(env.getId()));
        assertNotFound(() -> foreignClient.environments()
            .promoteContent(env.getId(), List.of(new ContentToPromoteDTO()), true));
    }

    @Test
    public void shouldFindEnvironmentByName() {
        EnvironmentDTO env = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());

        List<EnvironmentDTO> foundEnv = ownerClient.owners().listEnvironments(owner, env.getName());

        assertThat(foundEnv)
            .singleElement()
            .returns(env.getId(), EnvironmentDTO::getId);
    }

    @Test
    public void shouldFindTypedEnvironmentsByOwner() {
        EnvironmentDTO env1 = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random()
            .type(null));
        EnvironmentDTO env2 = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random()
            .type(StringUtil.random("type-")));
        EnvironmentDTO env3 = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random()
            .type(StringUtil.random("type-")));

        List<EnvironmentDTO> found1 = ownerClient.owners().listEnvironments(owner.getKey(), null,
            null, false);
        assertThat(found1).isNotNull().contains(env1).doesNotContain(env2, env3);
        List<EnvironmentDTO> found2 = ownerClient.owners().listEnvironments(owner.getKey(), null,
            List.of(env2.getType()), false);
        assertThat(found2).isNotNull().contains(env2).doesNotContain(env1, env3);
        List<EnvironmentDTO> found3 = ownerClient.owners().listEnvironments(owner.getKey(), null,
            List.of(env3.getType()), true);
        assertThat(found3).isNotNull().contains(env1, env2, env3);
    }

    @Test
    public void shouldLowerCaseEnvironmentType() {
        EnvironmentDTO env = ownerClient.owners().createEnvironment(owner.getKey(),
            Environments.random().type("mY_TypE"));

        List<EnvironmentDTO> foundEnv = ownerClient.owners().listEnvironments(owner, env.getName());

        assertThat(foundEnv)
            .singleElement()
            .returns("my_type", EnvironmentDTO::getType);
    }

    @Test
    public void shouldDefaultContentEnabledToNull() {
        EnvironmentDTO env = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        ContentDTO content = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());

        ContentToPromoteDTO contentToPromote = new ContentToPromoteDTO()
            .contentId(content.getId());
        promoteContent(env, List.of(contentToPromote));

        List<EnvironmentDTO> foundEnv = ownerClient.owners().listEnvironments(owner, env.getName());
        assertThat(foundEnv)
            .satisfies(environments -> assertContainsOnly(environments, env))
            .flatMap(EnvironmentDTO::getEnvironmentContent)
            .map(EnvironmentContentDTO::getEnabled)
            .containsOnlyNulls();
    }

    @Test
    public void shouldAllowEnabledContent() {
        EnvironmentDTO env = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        ContentDTO content = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());

        promoteContent(env, content);

        List<EnvironmentDTO> foundEnv = ownerClient.owners().listEnvironments(owner, env.getName());
        assertThat(foundEnv)
            .satisfies(environments -> assertContainsOnly(environments, env))
            .flatMap(EnvironmentDTO::getEnvironmentContent)
            .map(EnvironmentContentDTO::getEnabled)
            .containsOnly(true);
    }

    @Test
    public void shouldAllowDisabledContent() {
        EnvironmentDTO env = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        ContentDTO content = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());

        promoteContentDisabled(env, content);

        List<EnvironmentDTO> foundEnv = ownerClient.owners().listEnvironments(owner, env.getName());
        assertThat(foundEnv)
            .satisfies(environments -> assertContainsOnly(environments, env))
            .flatMap(EnvironmentDTO::getEnvironmentContent)
            .map(EnvironmentContentDTO::getEnabled)
            .containsOnly(false);
    }

    @Test
    public void shouldAllowContentPromotion() {
        EnvironmentDTO env = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        ContentDTO content = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());

        promoteContent(env, content);

        assertEnvContentSize(env, 1);
    }

    @Test
    public void shouldRejectPromotionOfAlreadyPromotedContent() {
        EnvironmentDTO env = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        ContentDTO content1 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
        ContentDTO content2 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());

        promoteContent(env, content1);
        assertConflict(() -> promoteContent(env, content2, content1));

        // The promotion of content2 should have been aborted due to the conflict with content1
        assertEnvContentSize(env, 1);
    }

    @Test
    public void shouldAllowPromotionOfMultipleContents() {
        EnvironmentDTO env = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        ContentDTO content1 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
        ContentDTO content2 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
        ContentDTO content3 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
        ContentDTO content4 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());

        promoteContent(env, content1);
        promoteContent(env, content2);
        promoteContent(env, content3, content4);

        assertEnvContentSize(env, 4);
    }

    @Test
    public void shouldCleanupEnvContentOnContentDeletion() {
        EnvironmentDTO env = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        ContentDTO content1 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
        ContentDTO content2 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());

        promoteContent(env, content1, content2);
        assertEnvContentSize(env, 2);

        ownerClient.ownerContent().removeContent(owner.getKey(), content1.getId());
        assertEnvContentSize(env, 1);

        ownerClient.ownerContent().removeContent(owner.getKey(), content2.getId());
        assertEnvContentSize(env, 0);
    }

    @Test
    public void shouldAllowContentDemotion() {
        EnvironmentDTO env = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        ContentDTO content1 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
        ContentDTO content2 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
        ContentDTO content3 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());

        promoteContent(env, content1, content2, content3);
        assertEnvContentSize(env, 3);

        demoteContent(env, content1, content3);
        assertEnvContentSize(env, 1);
    }

    @Test
    public void shouldGracefullyAbortDemotionOfInvalidContent() {
        EnvironmentDTO env = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        ContentDTO content1 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
        ContentDTO content2 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());

        promoteContent(env, content1, content2);
        assertEnvContentSize(env, 2);

        assertNotFound(() -> demoteContent(env, List.of("invalid")));
        assertEnvContentSize(env, 2);

        assertNotFound(() -> demoteContent(env, List.of(content1.getId(), "invalid", content2.getId())));
        assertEnvContentSize(env, 2);

        demoteContent(env, content1, content2);
        assertEnvContentSize(env, 0);
    }

    @Test
    public void shouldFilterContentNotPromotedToEnv() {
        EnvironmentDTO env = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        ConsumerDTO consumer = ownerClient.consumers().createConsumer(Consumers.random(owner)
            .putFactsItem("system.certificate_version", "1.0")
            .addEnvironmentsItem(env)
        );
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ProductDTO product = ownerClient.ownerProducts()
            .createProduct(owner.getKey(), Products.randomEng());
        ContentDTO content1 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
        ContentDTO content2 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
        ownerClient.ownerProducts()
            .addContentToProduct(owner.getKey(), product.getId(), content1.getId(), false);
        ownerClient.ownerProducts()
            .addContentToProduct(owner.getKey(), product.getId(), content2.getId(), false);

        promoteContentDisabled(env, content1);

        PoolDTO pool = ownerClient.owners().createPool(owner.getKey(), Pools.random(product));
        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 1);

        CertificateDTO cert = findFirstCert(entitlements);
        assertThatCert(cert)
            .hasContentRepoType(content1)
            .doesNotHaveContentRepoType(content2)
            .hasContentRepoDisabled(content1);
    }

    @Test
    public void shouldRegenerateCertsAfterPromotingOrDemotingContent() {
        EnvironmentDTO env = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        ConsumerDTO consumer = ownerClient.consumers().createConsumer(Consumers.random(owner)
            .putFactsItem("system.certificate_version", "1.0")
            .addEnvironmentsItem(env)
        );
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ProductDTO product = ownerClient.ownerProducts()
            .createProduct(owner.getKey(), Products.randomEng());
        ContentDTO content1 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
        ContentDTO content2 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
        ownerClient.ownerProducts()
            .addContentToProduct(owner.getKey(), product.getId(), content1.getId(), false);
        ownerClient.ownerProducts()
            .addContentToProduct(owner.getKey(), product.getId(), content2.getId(), false);
        promoteContent(env, content1);
        PoolDTO pool = ownerClient.owners().createPool(owner.getKey(), Pools.random(product));

        // Bind
        List<EntitlementDTO> entsAfterBind = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 1);
        CertificateDTO certAfterBind = findFirstCert(entsAfterBind);
        assertThatCert(certAfterBind)
            .hasContentRepoType(content1)
            .doesNotHaveContentRepoType(content2);
        Long serialAfterBind = certAfterBind.getSerial().getSerial();

        // Second content promoted
        promoteContent(env, content2);
        List<EntitlementDTO> entsAfterPromotion = ownerClient.consumers()
            .listEntitlementsWithRegen(consumer.getUuid());
        CertificateDTO certAfterPromotion = findFirstCert(entsAfterPromotion);
        assertThatCert(certAfterPromotion)
            .hasContentRepoType(content1)
            .hasContentRepoType(content2);
        Long serialAfterPromotion = certAfterPromotion.getSerial().getSerial();
        assertThat(serialAfterBind).isNotEqualTo(serialAfterPromotion);

        // Content demoted
        demoteContent(env, content2);
        List<EntitlementDTO> entsAfterDemotion = ownerClient.consumers()
            .listEntitlementsWithRegen(consumer.getUuid());
        CertificateDTO certAfterDemotion = findFirstCert(entsAfterDemotion);
        assertThatCert(certAfterDemotion)
            .hasContentRepoType(content1)
            .doesNotHaveContentRepoType(content2);
        Long serialAfterDemotion = certAfterDemotion.getSerial().getSerial();
        assertThat(serialAfterPromotion).isNotEqualTo(serialAfterDemotion);
    }

    @Test
    public void shouldListEnvironmentsWithPopulatedEntityCollections() {
        EnvironmentDTO env1 = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        EnvironmentDTO env2 = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        ContentDTO content1 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
        ContentDTO content2 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
        promoteContent(env1, content1);
        promoteContent(env1, content2);
        promoteContent(env2, content1);
        promoteContent(env2, content2);

        List<EnvironmentDTO> environments = ownerClient.owners().listEnvironments(owner);
        assertThat(environments)
            .hasSize(2)
            .map(EnvironmentDTO::getEnvironmentContent)
            .allSatisfy(content -> assertThat(content).hasSize(2));
    }

    @Nested
    class LegacyRegistrationEndpoint {

        @Test
        public void shouldCreateConsumerInSingleEnvironment() {
            EnvironmentDTO environment = ownerClient.owners()
                .createEnvironment(owner.getKey(), Environments.random());
            ConsumerDTO consumer = ownerClient.environments()
                .createConsumerInEnvironment(environment.getId(), Consumers.random(owner));

            assertThat(consumer.getEnvironments())
                .hasSize(1)
                .allSatisfy(env -> assertThat(env)
                    .returns(env.getId(), EnvironmentDTO::getId)
                    .returns(env.getName(), EnvironmentDTO::getName)
                );
        }

        @Test
        public void shouldCreateConsumerInMultipleEnvironments() {
            EnvironmentDTO env1 = ownerClient.owners()
                .createEnvironment(owner.getKey(), Environments.random());
            EnvironmentDTO env2 = ownerClient.owners()
                .createEnvironment(owner.getKey(), Environments.random());
            ConsumerDTO consumer2 = ownerClient.environments()
                .createConsumerInEnvironment(joinEnvIds(env2, env1), Consumers.random(owner));

            assertThat(consumer2.getEnvironment().getName().split(","))
                .hasSize(2)
                .containsExactly(env2.getName(), env1.getName());
            assertThat(consumer2.getEnvironments())
                .hasSize(2)
                .map(EnvironmentDTO::getId)
                .containsExactly(env2.getId(), env1.getId());
        }

        @Test
        public void shouldFailToCreateConsumerIfEnvironmentDoesNotExists() {
            assertNotFound(() -> ownerClient.environments()
                .createConsumerInEnvironment("randomEnv", Consumers.random(owner)));
        }

        @Test
        public void shouldPassActivationKeyAuth() {
            ProductDTO product = ownerClient.ownerProducts()
                .createProduct(owner.getKey(), Products.randomEng());
            PoolDTO pool = ownerClient.owners().createPool(owner.getKey(), Pools.random(product));
            EnvironmentDTO environment = ownerClient.owners()
                .createEnvironment(owner.getKey(), Environments.random());
            ActivationKeyDTO activationKey = ownerClient.owners()
                .createActivationKey(owner.getKey(), ActivationKeys.random(owner));
            ownerClient.activationKeys().addPoolToKey(activationKey.getId(), pool.getId(), 1L);

            ConsumerDTO consumer = Request.from(ApiClients.noAuth())
                .setPath("/environments/{env_id}/consumers")
                .setMethod("POST")
                .setPathParam("env_id", environment.getId())
                .addQueryParam("owner", owner.getKey())
                .addQueryParam("activation_keys", activationKey.getName())
                .setBody(Consumers.random(owner))
                .execute()
                .deserialize(ConsumerDTO.class);

            assertThat(consumer.getEnvironment().getName().split(","))
                .hasSize(1)
                .containsExactly(environment.getName());
            assertThat(consumer.getEnvironments())
                .hasSize(1)
                .map(EnvironmentDTO::getName)
                .containsExactly(environment.getName());
        }

    }

    @Test
    public void shouldGiveConsumerContentFromMultipleEnvironments() {
        ProductDTO product = ownerClient.ownerProducts()
            .createProduct(owner.getKey(), Products.randomEng());
        ContentDTO content1 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
        ContentDTO content2 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
        ContentDTO content3 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
        ownerClient.ownerProducts()
            .addContentToProduct(owner.getKey(), product.getId(), content1.getId(), true);
        ownerClient.ownerProducts()
            .addContentToProduct(owner.getKey(), product.getId(), content2.getId(), true);
        ownerClient.ownerProducts()
            .addContentToProduct(owner.getKey(), product.getId(), content3.getId(), true);
        PoolDTO pool = ownerClient.owners().createPool(owner.getKey(), Pools.random(product));

        EnvironmentDTO env1 = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        EnvironmentDTO env2 = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        EnvironmentDTO env3 = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        promoteContent(env1, content1);
        promoteContent(env2, content2);
        promoteContent(env3, content3);

        ConsumerDTO consumer = ownerClient.consumers().createConsumer(Consumers.random(owner)
            .environments(List.of(env1, env2, env3)));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumerClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);

        List<JsonNode> certificates = consumerClient.consumers().exportCertificates(consumer.getUuid(), null);
        assertThat(certificates).hasSize(1);

        assertThat(certificates.get(0).get("products")).hasSize(1);
        assertThat(certificates.get(0).get("products").get(0).get("content"))
            .hasSize(3)
            .map(jsonNode -> jsonNode.get("path").asText())
            .containsOnly(
                env1.getContentPrefix() + content1.getContentUrl(),
                env2.getContentPrefix() + content2.getContentUrl(),
                env3.getContentPrefix() + content3.getContentUrl()
            );
    }

    @Test
    public void shouldUseOwnerPrefixForContent() {
        OwnerDTO owner = admin.owners().createOwner(Owners.random().contentPrefix("/" + "test_owner"));
        ProductDTO product = ownerClient.ownerProducts()
            .createProduct(owner.getKey(), Products.randomEng());
        ContentDTO content1 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
        ContentDTO content2 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
        ContentDTO content3 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
        ownerClient.ownerProducts()
            .addContentToProduct(owner.getKey(), product.getId(), content1.getId(), true);
        ownerClient.ownerProducts()
            .addContentToProduct(owner.getKey(), product.getId(), content2.getId(), true);
        ownerClient.ownerProducts()
            .addContentToProduct(owner.getKey(), product.getId(), content3.getId(), true);
        PoolDTO pool = ownerClient.owners().createPool(owner.getKey(), Pools.random(product));

        EnvironmentDTO env1 = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        EnvironmentDTO env2 = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        EnvironmentDTO env3 = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        promoteContent(env1, content1);
        promoteContent(env2, content2);
        promoteContent(env3, content3);

        ConsumerDTO consumer = ownerClient.consumers().createConsumer(Consumers.random(owner)
            .environments(List.of(env1, env2, env3)));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumerClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);

        List<JsonNode> certificates = consumerClient.consumers().exportCertificates(consumer.getUuid(), null);
        assertThat(certificates).hasSize(1);

        assertThat(certificates.get(0).get("products")).hasSize(1);
        assertThat(certificates.get(0).get("products").get(0).get("content"))
            .hasSize(3)
            .map(jsonNode -> jsonNode.get("path").asText())
            .allSatisfy(path -> assertThat(path).startsWith(owner.getContentPrefix()));
    }

    @Test
    public void shouldUseEnvironmentPrefixForContent() {
        OwnerDTO owner = admin.owners().createOwner(Owners.random().contentPrefix("/test_owner" + "/$env"));
        ProductDTO product = ownerClient.ownerProducts()
            .createProduct(owner.getKey(), Products.randomEng());
        ContentDTO content1 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
        ContentDTO content2 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
        ContentDTO content3 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
        ownerClient.ownerProducts()
            .addContentToProduct(owner.getKey(), product.getId(), content1.getId(), true);
        ownerClient.ownerProducts()
            .addContentToProduct(owner.getKey(), product.getId(), content2.getId(), true);
        ownerClient.ownerProducts()
            .addContentToProduct(owner.getKey(), product.getId(), content3.getId(), true);
        PoolDTO pool = ownerClient.owners().createPool(owner.getKey(), Pools.random(product));

        EnvironmentDTO env1 = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        EnvironmentDTO env2 = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        EnvironmentDTO env3 = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        promoteContent(env1, content1);
        promoteContent(env2, content2);
        promoteContent(env3, content3);

        ConsumerDTO consumer = ownerClient.consumers().createConsumer(Consumers.random(owner)
            .environments(List.of(env1, env2, env3)));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumerClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);

        List<JsonNode> certificates = consumerClient.consumers().exportCertificates(consumer.getUuid(), null);
        assertThat(certificates).hasSize(1);

        assertThat(certificates.get(0).get("products")).hasSize(1);
        assertThat(certificates.get(0).get("products").get(0).get("content"))
            .hasSize(3)
            .map(jsonNode -> jsonNode.get("path").asText())
            .allSatisfy(path -> assertThat(path).containsAnyOf(
                env1.getName(),
                env2.getName(),
                env3.getName()
            ));
    }

    @Test
    public void shouldDeduplicateContentFromMultipleEnvironments() {
        OwnerDTO owner = admin.owners().createOwner(Owners.random().contentPrefix("/" + "test_owner/$env"));
        ProductDTO product = ownerClient.ownerProducts()
            .createProduct(owner.getKey(), Products.randomEng());
        ContentDTO content1 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
        ContentDTO content2 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
        ContentDTO content3 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
        ownerClient.ownerProducts()
            .addContentToProduct(owner.getKey(), product.getId(), content1.getId(), true);
        ownerClient.ownerProducts()
            .addContentToProduct(owner.getKey(), product.getId(), content2.getId(), true);
        ownerClient.ownerProducts()
            .addContentToProduct(owner.getKey(), product.getId(), content3.getId(), true);
        PoolDTO pool = ownerClient.owners().createPool(owner.getKey(), Pools.random(product));

        EnvironmentDTO env1 = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        EnvironmentDTO env2 = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        EnvironmentDTO env3 = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        promoteContent(env1, content1, content2);
        promoteContent(env2, content1, content2);
        promoteContent(env3, content1, content2, content3);

        ConsumerDTO consumer = ownerClient.consumers().createConsumer(Consumers.random(owner)
            .environments(List.of(env1, env2, env3)));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumerClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);

        List<JsonNode> certificates = consumerClient.consumers().exportCertificates(consumer.getUuid(), null);
        assertThat(certificates).hasSize(1);

        assertThat(certificates.get(0).get("products")).hasSize(1);
        assertThat(certificates.get(0).get("products").get(0).get("content"))
            .hasSize(3)
            .allSatisfy(jsonNode -> {
                String path = jsonNode.get("path").asText();
                if (content1.getId().equals(jsonNode.get("id").asText())) {
                    assertThat(path).contains(env1.getName());
                }
                else if (content2.getId().equals(jsonNode.get("id").asText())) {
                    assertThat(path).contains(env1.getName());
                }
                else {
                    assertThat(path).contains(env3.getName());
                }
            });
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = { false })
    public void shouldDeleteConsumersWhenLastEnvironmentIsDeleted(Boolean retainConsumers) {
        EnvironmentDTO env1 = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        EnvironmentDTO env2 = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        ConsumerDTO consumer1 = ownerClient.consumers().createConsumer(Consumers.random(owner)
            .environments(List.of(env1)));
        ConsumerDTO consumer2 = ownerClient.consumers().createConsumer(Consumers.random(owner)
            .environments(List.of(env1, env2)));

        ownerClient.environments().deleteEnvironment(env1.getId(), retainConsumers);

        assertGone(() -> ownerClient.consumers().getConsumer(consumer1.getUuid()));

        ConsumerDTO consumer = ownerClient.consumers().getConsumer(consumer2.getUuid());
        assertThat(consumer.getEnvironments()).hasSize(1);
    }

    @Test
    public void shouldRetainConsumerWhenDeletingItsLastEnvironmentIfRetainFlagIsSet() {
        EnvironmentDTO env1 = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        EnvironmentDTO env2 = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        ConsumerDTO consumer1 = ownerClient.consumers().createConsumer(Consumers.random(owner)
            .environments(List.of(env1)));
        ConsumerDTO consumer2 = ownerClient.consumers().createConsumer(Consumers.random(owner)
            .environments(List.of(env1, env2)));

        ownerClient.environments().deleteEnvironment(env1.getId(), true);

        ConsumerDTO consumer3 = ownerClient.consumers().getConsumer(consumer1.getUuid());
        assertThat(consumer3.getEnvironments()).isNull();

        ConsumerDTO consumer4 = ownerClient.consumers().getConsumer(consumer2.getUuid());
        assertThat(consumer4.getEnvironments()).hasSize(1);
    }

    @Test
    public void shouldRegenerateOnlyEntitlementsAffectedByDeletedEnvironment() {
        ProductDTO product1 = ownerClient.ownerProducts()
            .createProduct(owner.getKey(), Products.randomEng());
        ProductDTO product2 = ownerClient.ownerProducts()
            .createProduct(owner.getKey(), Products.randomEng());
        ContentDTO content1 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
        ContentDTO content2 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
        ownerClient.ownerProducts()
            .addContentToProduct(owner.getKey(), product1.getId(), content1.getId(), true);
        ownerClient.ownerProducts()
            .addContentToProduct(owner.getKey(), product2.getId(), content2.getId(), true);
        EnvironmentDTO env1 = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        EnvironmentDTO env2 = ownerClient.owners().createEnvironment(owner.getKey(), Environments.random());
        promoteContent(env1, content1);
        promoteContent(env2, content2);
        PoolDTO pool1 = ownerClient.owners().createPool(owner.getKey(), Pools.random(product1));
        PoolDTO pool2 = ownerClient.owners().createPool(owner.getKey(), Pools.random(product2));

        ConsumerDTO consumer = ownerClient.consumers().createConsumer(Consumers.random(owner)
            .putFactsItem(Facts.CertificateVersion.key(), "3.3")
            .environments(List.of(env1, env2)));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        EntitlementDTO entitlement1 = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool1.getId(), 1).get(0);
        EntitlementDTO entitlement2 = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool2.getId(), 1).get(0);
        Long pool1Serial = serialOf(entitlement1);
        Long pool2Serial = serialOf(entitlement2);

        List<JsonNode> certificatesBeforeDelete = consumerClient.consumers()
            .exportCertificates(consumer.getUuid(), null);
        assertThat(certificatesBeforeDelete).hasSize(2);

        ownerClient.environments().deleteEnvironment(env1.getId());

        List<EntitlementDTO> entitlements = consumerClient.consumers()
            .listEntitlementsWithRegen(consumer.getUuid());
        Set<Long> currentSerials = serialsOf(entitlements);

        assertThat(currentSerials)
            .isNotEmpty()
            .doesNotContain(pool1Serial)
            .contains(pool2Serial);

        List<CertificateDTO> certificates = consumerClient.consumers().fetchCertificates(consumer.getUuid());

        for (CertificateDTO certificate : certificates) {
            JsonNode entCert = CertificateUtil.decodeAndUncompressCertificate(
                certificate.getCert(), ApiClient.MAPPER);
            JsonNode products = entCert.get("products");
            assertThat(products).hasSize(1);
            JsonNode content = products.get(0).get("content");
            if (pool2Serial.equals(certificate.getSerial().getId())) {
                assertThat(content).hasSize(1);
            }
            else {
                assertThat(content).isEmpty();
            }
        }
    }

    @Test
    public void shouldAllowUpdatingNameWithEnvironmentUpdate() {
        OwnerDTO owner = admin.owners().createOwner(Owners.random());

        EnvironmentDTO environment = admin.owners()
            .createEnvironment(owner.getKey(), Environments.random());

        EnvironmentDTO update = new EnvironmentDTO()
            .name("updated name");

        EnvironmentDTO output = admin.environments()
            .updateEnvironment(environment.getId(), update);

        assertEquals(update.getName(), output.getName());

        EnvironmentDTO fetched = admin.environments()
            .getEnvironment(environment.getId());

        assertEquals(update.getName(), fetched.getName());
    }

    @Test
    public void shouldNotAllowEmptyNameWithEnvironmentUpdate() {
        OwnerDTO owner = admin.owners().createOwner(Owners.random());

        EnvironmentDTO environment = admin.owners()
            .createEnvironment(owner.getKey(), Environments.random());

        EnvironmentDTO update = new EnvironmentDTO()
            .name("");

        assertBadRequest(() -> admin.environments().updateEnvironment(environment.getId(), update));

        EnvironmentDTO fetched = admin.environments()
            .getEnvironment(environment.getId());

        assertEquals(environment.getName(), fetched.getName());
    }

    @Test
    public void shouldNotAllowLongNameWithEnvironmentUpdate() {
        OwnerDTO owner = admin.owners().createOwner(Owners.random());

        EnvironmentDTO environment = admin.owners()
            .createEnvironment(owner.getKey(), Environments.random());

        EnvironmentDTO update = new EnvironmentDTO()
            .name("long".repeat(256));

        assertBadRequest(() -> admin.environments()
            .updateEnvironment(environment.getId(), update));

        EnvironmentDTO result = admin.environments()
            .getEnvironment(environment.getId());

        assertEquals(environment.getName(), result.getName());
    }

    @Test
    public void shouldAllowUpdatingTypeWithEnvironmentUpdate() {
        OwnerDTO owner = admin.owners().createOwner(Owners.random());

        EnvironmentDTO environment = admin.owners()
            .createEnvironment(owner.getKey(), Environments.random());

        EnvironmentDTO update = new EnvironmentDTO()
            .type("updated type");

        EnvironmentDTO output = admin.environments()
            .updateEnvironment(environment.getId(), update);

        assertEquals(update.getType(), output.getType());

        EnvironmentDTO fetched = admin.environments()
            .getEnvironment(environment.getId());

        assertEquals(update.getType(), fetched.getType());
    }

    @Test
    public void shouldAllowClearingTypeWithEnvironmentUpdate() {
        OwnerDTO owner = admin.owners().createOwner(Owners.random());

        EnvironmentDTO environment = admin.owners()
            .createEnvironment(owner.getKey(), Environments.random());

        EnvironmentDTO update = new EnvironmentDTO()
            .type("");

        EnvironmentDTO output = admin.environments()
            .updateEnvironment(environment.getId(), update);

        assertNull(output.getType());

        EnvironmentDTO fetched = admin.environments()
            .getEnvironment(environment.getId());

        assertNull(fetched.getType());
    }

    @Test
    public void shouldNotAllowLongTypeWithEnvironmentUpdate() {
        OwnerDTO owner = admin.owners().createOwner(Owners.random());

        EnvironmentDTO environment = admin.owners()
            .createEnvironment(owner.getKey(), Environments.random());

        EnvironmentDTO update = new EnvironmentDTO()
            .type("long".repeat(256));

        assertBadRequest(() -> admin.environments()
            .updateEnvironment(environment.getId(), update));

        EnvironmentDTO result = admin.environments()
            .getEnvironment(environment.getId());

        assertEquals(environment.getType(), result.getType());
    }

    @Test
    public void shouldAllowUpdatingDescriptionWithEnvironmentUpdate() {
        OwnerDTO owner = admin.owners().createOwner(Owners.random());

        EnvironmentDTO environment = admin.owners()
            .createEnvironment(owner.getKey(), Environments.random());

        EnvironmentDTO update = new EnvironmentDTO()
            .description("updated description");

        EnvironmentDTO output = admin.environments()
            .updateEnvironment(environment.getId(), update);

        assertEquals(update.getDescription(), output.getDescription());

        EnvironmentDTO fetched = admin.environments()
            .getEnvironment(environment.getId());

        assertEquals(update.getDescription(), fetched.getDescription());
    }

    @Test
    public void shouldAllowClearingDescriptionWithEnvironmentUpdate() {
        OwnerDTO owner = admin.owners().createOwner(Owners.random());

        EnvironmentDTO environment = admin.owners()
            .createEnvironment(owner.getKey(), Environments.random());

        EnvironmentDTO update = new EnvironmentDTO()
            .description("");

        EnvironmentDTO output = admin.environments()
            .updateEnvironment(environment.getId(), update);

        assertNull(output.getDescription());

        EnvironmentDTO fetched = admin.environments()
            .getEnvironment(environment.getId());

        assertNull(fetched.getDescription());
    }

    @Test
    public void shouldNotAllowLongDescriptionWithEnvironmentUpdate() {
        OwnerDTO owner = admin.owners().createOwner(Owners.random());

        EnvironmentDTO environment = admin.owners()
            .createEnvironment(owner.getKey(), Environments.random());

        EnvironmentDTO update = new EnvironmentDTO()
            .description("long".repeat(256));

        assertBadRequest(() -> admin.environments()
            .updateEnvironment(environment.getId(), update));

        EnvironmentDTO result = admin.environments()
            .getEnvironment(environment.getId());

        assertEquals(environment.getDescription(), result.getDescription());
    }

    @Test
    public void shouldAllowUpdatingContentPrefixWithEnvironmentUpdate() {
        OwnerDTO owner = admin.owners().createOwner(Owners.random());

        EnvironmentDTO environment = admin.owners()
            .createEnvironment(owner.getKey(), Environments.random());

        EnvironmentDTO update = new EnvironmentDTO()
            .contentPrefix("updated contentprefix");

        EnvironmentDTO output = admin.environments()
            .updateEnvironment(environment.getId(), update);

        assertEquals(update.getContentPrefix(), output.getContentPrefix());

        EnvironmentDTO fetched = admin.environments()
            .getEnvironment(environment.getId());

        assertEquals(update.getContentPrefix(), fetched.getContentPrefix());
    }

    @Test
    public void shouldAllowClearingContentPrefixWithEnvironmentUpdate() {
        OwnerDTO owner = admin.owners().createOwner(Owners.random());

        EnvironmentDTO environment = admin.owners()
            .createEnvironment(owner.getKey(), Environments.random());

        EnvironmentDTO update = new EnvironmentDTO()
            .contentPrefix("");

        EnvironmentDTO output = admin.environments()
            .updateEnvironment(environment.getId(), update);

        assertNull(output.getContentPrefix());

        EnvironmentDTO fetched = admin.environments()
            .getEnvironment(environment.getId());

        assertNull(fetched.getContentPrefix());
    }

    @Test
    public void shouldNotAllowLongContentPrefixWithEnvironmentUpdate() {
        EnvironmentDTO environment = admin.owners()
            .createEnvironment(this.owner.getKey(), Environments.random());

        EnvironmentDTO update = new EnvironmentDTO()
            .contentPrefix("long".repeat(256));

        assertBadRequest(() -> admin.environments()
            .updateEnvironment(environment.getId(), update));

        EnvironmentDTO result = admin.environments()
            .getEnvironment(environment.getId());

        assertEquals(environment.getContentPrefix(), result.getContentPrefix());
    }


    /**
     * These tests verify we don't update fields that either should not be updated (ID, owner) or
     * need special juggling for cert and content access (env content)
     */
    @Test
    public void shouldIgnoreIdChangesWithEnvironmentUpdate() {
        OwnerDTO owner = admin.owners().createOwner(Owners.random());

        EnvironmentDTO environment = admin.owners()
            .createEnvironment(owner.getKey(), Environments.random());

        EnvironmentDTO update = new EnvironmentDTO()
            .id("updated id");

        EnvironmentDTO output = admin.environments()
            .updateEnvironment(environment.getId(), update);

        assertEquals(environment.getId(), output.getId());

        EnvironmentDTO fetched = admin.environments()
            .getEnvironment(environment.getId());

        assertEquals(environment.getId(), fetched.getId());
    }

    @Test
    public void shouldIgnoreCreatedTimestampChangesWithEnvironmentUpdate() {
        OwnerDTO owner = admin.owners().createOwner(Owners.random());

        EnvironmentDTO environment = admin.owners()
            .createEnvironment(owner.getKey(), Environments.random());

        EnvironmentDTO update = new EnvironmentDTO()
            .created(OffsetDateTime.now().minusDays(5));

        EnvironmentDTO output = admin.environments()
            .updateEnvironment(environment.getId(), update);

        assertEquals(environment.getCreated(), output.getCreated());

        EnvironmentDTO fetched = admin.environments()
            .getEnvironment(environment.getId());

        assertEquals(environment.getCreated(), fetched.getCreated());
    }

    @Test
    public void shouldIgnoreUpdatedTimestampChangesWithEnvironmentUpdate() {
        OwnerDTO owner = admin.owners().createOwner(Owners.random());

        EnvironmentDTO environment = admin.owners()
            .createEnvironment(owner.getKey(), Environments.random());

        EnvironmentDTO update = new EnvironmentDTO()
            .updated(OffsetDateTime.now().minusDays(5));

        EnvironmentDTO output = admin.environments()
            .updateEnvironment(environment.getId(), update);

        assertEquals(environment.getUpdated(), output.getUpdated());

        EnvironmentDTO fetched = admin.environments()
            .getEnvironment(environment.getId());

        assertEquals(environment.getUpdated(), fetched.getUpdated());
    }

    @Test
    public void shouldIgnoreOwnerChangesWithEnvironmentUpdate() {
        OwnerDTO owner = admin.owners().createOwner(Owners.random());

        EnvironmentDTO environment = admin.owners()
            .createEnvironment(owner.getKey(), Environments.random());

        EnvironmentDTO update = new EnvironmentDTO()
            .owner(Owners.toNested(Owners.random()));

        EnvironmentDTO output = admin.environments()
            .updateEnvironment(environment.getId(), update);

        assertThat(output)
            .isNotNull()
            .extracting(EnvironmentDTO::getOwner)
            .usingRecursiveComparison()
            .isEqualTo(environment.getOwner());

        EnvironmentDTO fetched = admin.environments()
            .getEnvironment(environment.getId());

        assertThat(fetched)
            .isNotNull()
            .extracting(EnvironmentDTO::getOwner)
            .usingRecursiveComparison()
            .isEqualTo(environment.getOwner());
    }

    @Test
    public void shouldIgnoreEnvironmentContentChangesWithEnvironmentUpdate() {
        OwnerDTO owner = admin.owners().createOwner(Owners.random());
        ContentDTO content1 = admin.ownerContent().createContent(owner.getKey(), Contents.random());
        ContentDTO content2 = admin.ownerContent().createContent(owner.getKey(), Contents.random());

        EnvironmentDTO environment = admin.owners()
            .createEnvironment(owner.getKey(), Environments.random());

        this.promoteContent(environment, content1);

        environment = admin.environments().getEnvironment(environment.getId());

        EnvironmentContentDTO envcont2 = new EnvironmentContentDTO()
            .contentId(content2.getId())
            .enabled(true);

        EnvironmentDTO update = new EnvironmentDTO()
            .addEnvironmentContentItem(envcont2);

        EnvironmentDTO output = admin.environments()
            .updateEnvironment(environment.getId(), update);

        assertThat(output)
            .isNotNull()
            .extracting(EnvironmentDTO::getEnvironmentContent)
            .isEqualTo(environment.getEnvironmentContent());

        EnvironmentDTO fetched = admin.environments().getEnvironment(environment.getId());

        assertThat(fetched)
            .isNotNull()
            .extracting(EnvironmentDTO::getEnvironmentContent)
            .isEqualTo(environment.getEnvironmentContent());
    }

    /**
     * Test to verify that we don't do partial updates in the event that we have multiple fields to
     * update, some valid and some not.
     */
    @ParameterizedTest
    @ValueSource(strings = { "name", "type", "description", "contentPrefix" })
    public void shouldUpdateEnvironmentAtomically(String invalidFieldName) {
        OwnerDTO owner = admin.owners().createOwner(Owners.random());

        EnvironmentDTO environment = admin.owners()
            .createEnvironment(owner.getKey(), Environments.random());

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

        assertBadRequest(() -> admin.environments()
            .updateEnvironment(environment.getId(), update));

        EnvironmentDTO fetched = admin.environments().getEnvironment(environment.getId());

        assertThat(fetched)
            .isNotNull()
            .usingRecursiveComparison()
            .isEqualTo(environment);
    }

    private Set<Long> serialsOf(Collection<EntitlementDTO> entitlements) {
        return entitlements.stream()
            .map(this::serialOf)
            .collect(Collectors.toSet());
    }

    private Long serialOf(EntitlementDTO entitlement) {
        return entitlement.getCertificates().stream()
            .map(CertificateDTO::getSerial)
            .map(CertificateSerialDTO::getId)
            .findFirst().orElseThrow();
    }

    private CertificateDTO findFirstCert(List<EntitlementDTO> entitlements) {
        return entitlements.stream()
            .map(EntitlementDTO::getCertificates)
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .findFirst()
            .orElseThrow();
    }

    private String joinEnvIds(EnvironmentDTO... envs) {
        return Arrays.stream(envs)
            .map(EnvironmentDTO::getId)
            .collect(Collectors.joining(","));
    }

    private void assertEnvContentSize(EnvironmentDTO env, int expected) {
        EnvironmentDTO foundEnv = ownerClient.environments().getEnvironment(env.getId());
        assertThat(foundEnv.getEnvironmentContent())
            .hasSize(expected);
    }

    private void assertContainsOnly(List<? extends EnvironmentDTO> environments, EnvironmentDTO env) {
        assertThat(environments)
            .map(EnvironmentDTO::getId)
            .containsOnly(env.getId());
    }

    private void demoteContent(EnvironmentDTO env, ContentDTO... content) {
        List<String> contentToDemote = Arrays.stream(content)
            .map(ContentDTO::getId)
            .collect(Collectors.toList());

        demoteContent(env, contentToDemote);
    }

    private void demoteContent(EnvironmentDTO env, List<String> contentIds) {
        AsyncJobStatusDTO job = ownerClient.environments()
            .demoteContent(env.getId(), contentIds, null);
        ownerClient.jobs().waitForJob(job);
    }

    private void promoteContent(EnvironmentDTO env, ContentDTO... content) {
        List<ContentToPromoteDTO> contentToPromote = Arrays.stream(content)
            .map(this::toPromote)
            .collect(Collectors.toList());

        promoteContent(env, contentToPromote);
    }

    private void promoteContent(EnvironmentDTO env, List<ContentToPromoteDTO> contentToPromote) {
        AsyncJobStatusDTO job = ownerClient.environments()
            .promoteContent(env.getId(), contentToPromote, null);
        ownerClient.jobs().waitForJob(job);
    }

    private void promoteContentDisabled(EnvironmentDTO env, ContentDTO... content) {
        List<ContentToPromoteDTO> contentToPromote = Arrays.stream(content)
            .map(this::toPromote)
            .map(promote -> promote.enabled(false))
            .collect(Collectors.toList());

        promoteContent(env, contentToPromote);
    }

    private ContentToPromoteDTO toPromote(ContentDTO content) {
        return new ContentToPromoteDTO()
            .contentId(content.getId())
            .enabled(true);
    }

}
