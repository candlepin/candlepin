package org.candlepin.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.CertificateAssert.assertThatCert;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertConflict;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertGone;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;

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

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@SpecTest
public class AaTesting {
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
    public void shouldRegenerateCertsAfterPromotingOrDemotingContent() {
        EnvironmentDTO env = ownerClient.owners().createEnv(owner.getKey(), Environments.random());
        ConsumerDTO consumer = ownerClient.consumers().createConsumer(Consumers.random(owner)
            .putFactsItem("system.certificate_version", "1.0")
            .addEnvironmentsItem(env)
        );
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ProductDTO product = ownerClient.ownerProducts()
            .createProduct(owner.getKey(), Products.randomEng());

        for (int i=0; i<1000; i++) {
            ContentDTO content1 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
            ContentDTO content2 = ownerClient.ownerContent().createContent(owner.getKey(), Contents.random());
            ownerClient.ownerProducts()
                .addContentToProduct(owner.getKey(), product.getId(), content1.getId(), false);
            ownerClient.ownerProducts()
                .addContentToProduct(owner.getKey(), product.getId(), content2.getId(), false);
            // promoteContent(env, content1);
            // PoolDTO pool = ownerClient.owners().createPool(owner.getKey(), Pools.random(product));
        }

        // // Bind
        // List<EntitlementDTO> entsAfterBind = consumerClient.consumers()
        //     .bindPoolSync(consumer.getUuid(), pool.getId(), 1);
        // CertificateDTO certAfterBind = findFirstCert(entsAfterBind);
        // assertThatCert(certAfterBind)
        //     .hasContentRepoType(content1)
        //     .doesNotHaveContentRepoType(content2);
        // Long serialAfterBind = certAfterBind.getSerial().getSerial();

        // // Second content promoted
        // promoteContent(env, content2);
        // List<EntitlementDTO> entsAfterPromotion = ownerClient.consumers()
        //     .listEntitlementsWithRegen(consumer.getUuid());
        // CertificateDTO certAfterPromotion = findFirstCert(entsAfterPromotion);
        // assertThatCert(certAfterPromotion)
        //     .hasContentRepoType(content1)
        //     .hasContentRepoType(content2);
        // Long serialAfterPromotion = certAfterPromotion.getSerial().getSerial();
        // assertThat(serialAfterBind).isNotEqualTo(serialAfterPromotion);

        // // Content demoted
        // demoteContent(env, content2);
        // List<EntitlementDTO> entsAfterDemotion = ownerClient.consumers()
        //     .listEntitlementsWithRegen(consumer.getUuid());
        // CertificateDTO certAfterDemotion = findFirstCert(entsAfterDemotion);
        // assertThatCert(certAfterDemotion)
        //     .hasContentRepoType(content1)
        //     .doesNotHaveContentRepoType(content2);
        // Long serialAfterDemotion = certAfterDemotion.getSerial().getSerial();
        // assertThat(serialAfterPromotion).isNotEqualTo(serialAfterDemotion);
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
