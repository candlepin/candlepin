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

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.CertificateDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.ContentToPromoteDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.EnvironmentDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.Environments;
import org.candlepin.spec.bootstrap.data.builder.Facts;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


@SpecTest
@SuppressWarnings("indentation")
public class EnvironmentPrioritySpecTest {

    private static ApiClient admin;

    private OwnerDTO owner;
    private ApiClient ownerClient;

    private EnvironmentDTO env1;
    private EnvironmentDTO env2;
    private ContentDTO content1;
    private ContentDTO content2;
    private ContentDTO content3;

    @BeforeAll
    public static void beforeAll() {
        admin = ApiClients.admin();
    }

    @BeforeEach
    void setUp() {
        this.owner = admin.owners().createOwner(Owners.random());
        this.ownerClient = ApiClients.basic(UserUtil.createAdminUser(admin, this.owner));

        this.env1 = ownerClient.owners().createEnv(owner.getKey(), Environments.random());
        this.env2 = ownerClient.owners().createEnv(owner.getKey(), Environments.random());
        this.content1 = createContent();
        this.content2 = createContent();
        this.content3 = createContent();
    }

    @Test
    public void shouldRegenWhenAddingHigherPriorityEnvironmentWithDifferentContent() {
        ProductDTO product = ownerClient.ownerProducts()
            .createProductByOwner(owner.getKey(), Products.randomEng());
        addContentToProduct(product, content1);
        addContentToProduct(product, content2);
        addContentToProduct(product, content3);
        promoteContent(env1, content2);
        promoteContent(env2, content3);
        PoolDTO pool = ownerClient.owners().createPool(owner.getKey(), Pools.random(product));

        ConsumerDTO consumer = createConsumer(env1);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        EntitlementDTO entitlement = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 1).get(0);
        CertificateDTO firstCert = findFirstCert(entitlement);
        Long oldSerial = firstCert.getSerial().getSerial();

        assertThatCert(firstCert)
            .doesNotHaveContentRepoType(content1)
            .hasContentRepoType(content2)
            .doesNotHaveContentRepoType(content3);

        ownerClient.consumers()
            .updateConsumer(consumer.getUuid(), consumer.environments(List.of(env2, env1)));
        List<EntitlementDTO> entsWithSecondEnvironment = ownerClient.consumers()
            .listEntitlementsWithRegen(consumer.getUuid());
        CertificateDTO secondCert = findFirstCert(entsWithSecondEnvironment);
        Long newSerial = secondCert.getSerial().getSerial();

        assertThat(newSerial).isNotEqualTo(oldSerial);
        assertThatCert(secondCert)
            .doesNotHaveContentRepoType(content1)
            .hasContentRepoType(content2)
            .hasContentRepoType(content3);
    }

    @Test
    public void shouldRegenWhenAddingHigherPriorityEnvironmentWithDifferentContentProduction() {
        ProductDTO providedProduct = ownerClient.ownerProducts()
            .createProductByOwner(owner.getKey(), Products.randomEng());
        ProductDTO product = ownerClient.ownerProducts().createProductByOwner(owner.getKey(),
            Products.randomEng().addProvidedProductsItem(providedProduct));
        addContentToProduct(providedProduct, content1);
        addContentToProduct(providedProduct, content2);
        addContentToProduct(providedProduct, content3);
        promoteContent(env1, content2);
        promoteContent(env2, content3);
        PoolDTO pool = ownerClient.owners().createPool(owner.getKey(), Pools.random(product));

        ConsumerDTO consumer = createConsumer(env1);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        EntitlementDTO entitlement = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 1).get(0);
        CertificateDTO firstCert = findFirstCert(entitlement);
        Long oldSerial = firstCert.getSerial().getSerial();

        assertThatCert(firstCert)
            .doesNotHaveContentRepoType(content1)
            .hasContentRepoType(content2)
            .doesNotHaveContentRepoType(content3);

        ownerClient.consumers()
            .updateConsumer(consumer.getUuid(), consumer.environments(List.of(env2, env1)));
        List<EntitlementDTO> entsWithSecondEnvironment = ownerClient.consumers()
            .listEntitlementsWithRegen(consumer.getUuid());
        CertificateDTO secondCert = findFirstCert(entsWithSecondEnvironment);
        Long newSerial = secondCert.getSerial().getSerial();

        assertThat(newSerial).isNotEqualTo(oldSerial);
        assertThatCert(secondCert)
            .doesNotHaveContentRepoType(content1)
            .hasContentRepoType(content2)
            .hasContentRepoType(content3);
    }

    @Test
    public void shouldNotRegenWhenAddingLowerPriorityEnvironmentWithSameContent() {
        ProductDTO product = ownerClient.ownerProducts()
            .createProductByOwner(owner.getKey(), Products.randomEng());
        addContentToProduct(product, content1);
        addContentToProduct(product, content2);
        addContentToProduct(product, content3);
        promoteContent(env1, content2);
        promoteContent(env2, content2);
        PoolDTO pool = ownerClient.owners().createPool(owner.getKey(), Pools.random(product));

        ConsumerDTO consumer = createConsumer(env1);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        EntitlementDTO entitlement = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 1).get(0);
        CertificateDTO firstCert = findFirstCert(entitlement);
        Long oldSerial = firstCert.getSerial().getSerial();

        assertThatCert(firstCert)
            .doesNotHaveContentRepoType(content1)
            .hasContentRepoType(content2)
            .doesNotHaveContentRepoType(content3);

        ownerClient.consumers().updateConsumer(consumer.getUuid(), consumer.addEnvironmentsItem(env2));
        List<EntitlementDTO> entsWithSecondEnvironment = ownerClient.consumers()
            .listEntitlementsWithRegen(consumer.getUuid());
        CertificateDTO secondCert = findFirstCert(entsWithSecondEnvironment);
        Long newSerial = secondCert.getSerial().getSerial();

        assertThat(newSerial).isEqualTo(oldSerial);
        assertThatCert(secondCert)
            .doesNotHaveContentRepoType(content1)
            .doesNotHaveContentRepoType(content3)
            .hasContentRepoType(content2);
    }

    @Test
    public void shouldRegenWhenAddingLowerPriorityEnvironmentWithDifferentContent() {
        ProductDTO product = ownerClient.ownerProducts()
            .createProductByOwner(owner.getKey(), Products.randomEng());
        addContentToProduct(product, content1);
        addContentToProduct(product, content2);
        addContentToProduct(product, content3);
        promoteContent(env1, content2);
        promoteContent(env2, content3);
        PoolDTO pool = ownerClient.owners().createPool(owner.getKey(), Pools.random(product));

        ConsumerDTO consumer = createConsumer(env1);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        EntitlementDTO entitlement = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 1).get(0);
        CertificateDTO firstCert = findFirstCert(entitlement);
        Long oldSerial = firstCert.getSerial().getSerial();

        assertThatCert(firstCert)
            .doesNotHaveContentRepoType(content1)
            .hasContentRepoType(content2)
            .doesNotHaveContentRepoType(content3);

        ownerClient.consumers().updateConsumer(consumer.getUuid(), consumer.addEnvironmentsItem(env2));
        List<EntitlementDTO> entsWithSecondEnvironment = ownerClient.consumers()
            .listEntitlementsWithRegen(consumer.getUuid());
        CertificateDTO secondCert = findFirstCert(entsWithSecondEnvironment);
        Long newSerial = secondCert.getSerial().getSerial();

        assertThat(newSerial).isNotEqualTo(oldSerial);
        assertThatCert(secondCert)
            .doesNotHaveContentRepoType(content1)
            .hasContentRepoType(content2)
            .hasContentRepoType(content3);
    }

    @Test
    public void shouldNotRegenWhenAddingEnvironmentWithoutContent() {
        ProductDTO product = ownerClient.ownerProducts()
            .createProductByOwner(owner.getKey(), Products.randomEng());
        addContentToProduct(product, content1);
        addContentToProduct(product, content2);
        addContentToProduct(product, content3);
        promoteContent(env1, content2);
        PoolDTO pool = ownerClient.owners().createPool(owner.getKey(), Pools.random(product));

        ConsumerDTO consumer = createConsumer(env1);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        EntitlementDTO entitlement = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 1).get(0);
        CertificateDTO firstCert = findFirstCert(entitlement);
        Long oldSerial = firstCert.getSerial().getSerial();

        assertThatCert(firstCert)
            .doesNotHaveContentRepoType(content1)
            .hasContentRepoType(content2)
            .doesNotHaveContentRepoType(content3);

        ownerClient.consumers().updateConsumer(consumer.getUuid(), consumer.addEnvironmentsItem(env2));
        List<EntitlementDTO> entsWithSecondEnvironment = ownerClient.consumers()
            .listEntitlementsWithRegen(consumer.getUuid());
        CertificateDTO secondCert = findFirstCert(entsWithSecondEnvironment);
        Long newSerial = secondCert.getSerial().getSerial();

        assertThat(newSerial).isEqualTo(oldSerial);
        assertThatCert(secondCert)
            .doesNotHaveContentRepoType(content1)
            .hasContentRepoType(content2)
            .doesNotHaveContentRepoType(content3);
    }

    @Test
    public void shouldRegenWhenReorderingEnvironmentsWithSameContent() {
        ProductDTO product = ownerClient.ownerProducts()
            .createProductByOwner(owner.getKey(), Products.randomEng());
        addContentToProduct(product, content1);
        addContentToProduct(product, content2);
        addContentToProduct(product, content3);
        promoteContent(env1, content2);
        promoteContent(env2, content2);
        PoolDTO pool = ownerClient.owners().createPool(owner.getKey(), Pools.random(product));

        ConsumerDTO consumer = createConsumer(env1, env2);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        EntitlementDTO entitlement = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 1).get(0);
        CertificateDTO firstCert = findFirstCert(entitlement);
        Long oldSerial = firstCert.getSerial().getSerial();

        assertThatCert(firstCert)
            .doesNotHaveContentRepoType(content1)
            .hasContentRepoType(content2)
            .doesNotHaveContentRepoType(content3);

        ownerClient.consumers()
            .updateConsumer(consumer.getUuid(), consumer.environments(List.of(env2, env1)));
        List<EntitlementDTO> entsWithSecondEnvironment = ownerClient.consumers()
            .listEntitlementsWithRegen(consumer.getUuid());
        CertificateDTO secondCert = findFirstCert(entsWithSecondEnvironment);
        Long newSerial = secondCert.getSerial().getSerial();

        assertThat(newSerial).isNotEqualTo(oldSerial);
        assertThatCert(secondCert)
            .doesNotHaveContentRepoType(content1)
            .hasContentRepoType(content2)
            .doesNotHaveContentRepoType(content3);
    }

    @Test
    public void shouldNotRegenWhenReorderingLowPriorityEnvironments() {
        EnvironmentDTO env3 = ownerClient.owners().createEnv(owner.getKey(), Environments.random());
        ProductDTO product = ownerClient.ownerProducts()
            .createProductByOwner(owner.getKey(), Products.randomEng());
        addContentToProduct(product, content1);
        addContentToProduct(product, content2);
        addContentToProduct(product, content3);
        promoteContent(env1, content2);
        promoteContent(env2, content2);
        promoteContent(env3, content2);
        PoolDTO pool = ownerClient.owners().createPool(owner.getKey(), Pools.random(product));

        ConsumerDTO consumer = createConsumer(env1, env2, env3);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        EntitlementDTO entitlement = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 1).get(0);
        CertificateDTO firstCert = findFirstCert(entitlement);
        Long oldSerial = firstCert.getSerial().getSerial();

        assertThatCert(firstCert)
            .doesNotHaveContentRepoType(content1)
            .hasContentRepoType(content2)
            .doesNotHaveContentRepoType(content3);

        ownerClient.consumers()
            .updateConsumer(consumer.getUuid(), consumer.environments(List.of(env1, env3, env2)));
        List<EntitlementDTO> entsWithSecondEnvironment = ownerClient.consumers()
            .listEntitlementsWithRegen(consumer.getUuid());
        CertificateDTO secondCert = findFirstCert(entsWithSecondEnvironment);
        Long newSerial = secondCert.getSerial().getSerial();

        assertThat(newSerial).isEqualTo(oldSerial);
        assertThatCert(secondCert)
            .doesNotHaveContentRepoType(content1)
            .hasContentRepoType(content2)
            .doesNotHaveContentRepoType(content3);
    }

    @Test
    public void shouldRegenWhenReorderingEnvironmentsWithoutSpecificContent() {
        ProductDTO product = ownerClient.ownerProducts()
            .createProductByOwner(owner.getKey(), Products.randomEng());
        ContentDTO content4 = createContent();
        addContentToProduct(product, content1);
        addContentToProduct(product, content2);
        addContentToProduct(product, content3);
        promoteContent(env1, content2);
        promoteContent(env2, content4);
        promoteContent(env2, content2);
        PoolDTO pool = ownerClient.owners().createPool(owner.getKey(), Pools.random(product));

        ConsumerDTO consumer = createConsumer(env1, env2);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        EntitlementDTO entitlement = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 1).get(0);
        CertificateDTO firstCert = findFirstCert(entitlement);
        Long oldSerial = firstCert.getSerial().getSerial();

        assertThatCert(firstCert)
            .doesNotHaveContentRepoType(content1)
            .hasContentRepoType(content2)
            .doesNotHaveContentRepoType(content3)
            .doesNotHaveContentRepoType(content4);

        ownerClient.consumers()
            .updateConsumer(consumer.getUuid(), consumer.environments(List.of(env2, env1)));
        List<EntitlementDTO> entsWithSecondEnvironment = ownerClient.consumers()
            .listEntitlementsWithRegen(consumer.getUuid());
        CertificateDTO secondCert = findFirstCert(entsWithSecondEnvironment);
        Long newSerial = secondCert.getSerial().getSerial();

        assertThat(newSerial).isNotEqualTo(oldSerial);
        assertThatCert(secondCert)
            .doesNotHaveContentRepoType(content1)
            .hasContentRepoType(content2)
            .doesNotHaveContentRepoType(content3)
            .doesNotHaveContentRepoType(content4);
    }

    @Test
    public void shouldRegenWhenRemovingEnvironmentWihContentSameAsLowerEnvironment() {
        ProductDTO product = ownerClient.ownerProducts()
            .createProductByOwner(owner.getKey(), Products.randomEng());
        addContentToProduct(product, content1);
        addContentToProduct(product, content2);
        addContentToProduct(product, content3);
        promoteContent(env1, content2);
        promoteContent(env2, content2);
        PoolDTO pool = ownerClient.owners().createPool(owner.getKey(), Pools.random(product));

        ConsumerDTO consumer = createConsumer(env1, env2);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        EntitlementDTO entitlement = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 1).get(0);
        CertificateDTO firstCert = findFirstCert(entitlement);
        Long oldSerial = firstCert.getSerial().getSerial();

        assertThatCert(firstCert)
            .doesNotHaveContentRepoType(content1)
            .hasContentRepoType(content2)
            .doesNotHaveContentRepoType(content3);

        ownerClient.environments().deleteEnvironment(env1.getId());

        List<EntitlementDTO> entsWithSecondEnvironment = ownerClient.consumers()
            .listEntitlementsWithRegen(consumer.getUuid());
        CertificateDTO secondCert = findFirstCert(entsWithSecondEnvironment);
        Long newSerial = secondCert.getSerial().getSerial();

        assertThat(newSerial).isNotEqualTo(oldSerial);
        assertThatCert(secondCert)
            .doesNotHaveContentRepoType(content1)
            .hasContentRepoType(content2)
            .doesNotHaveContentRepoType(content3);
    }

    @Test
    public void shouldNotRegenWhenRemovingEnvironmentWihContentSameAsHigherEnvironment() {
        ProductDTO product = ownerClient.ownerProducts()
            .createProductByOwner(owner.getKey(), Products.randomEng());
        addContentToProduct(product, content1);
        addContentToProduct(product, content2);
        addContentToProduct(product, content3);
        promoteContent(env1, content2);
        promoteContent(env2, content2);
        PoolDTO pool = ownerClient.owners().createPool(owner.getKey(), Pools.random(product));

        ConsumerDTO consumer = createConsumer(env1, env2);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        EntitlementDTO entitlement = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 1).get(0);
        CertificateDTO firstCert = findFirstCert(entitlement);
        Long oldSerial = firstCert.getSerial().getSerial();

        assertThatCert(firstCert)
            .doesNotHaveContentRepoType(content1)
            .hasContentRepoType(content2)
            .doesNotHaveContentRepoType(content3);

        ownerClient.environments().deleteEnvironment(env2.getId());

        List<EntitlementDTO> entsWithSecondEnvironment = ownerClient.consumers()
            .listEntitlementsWithRegen(consumer.getUuid());
        CertificateDTO secondCert = findFirstCert(entsWithSecondEnvironment);
        Long newSerial = secondCert.getSerial().getSerial();

        assertThat(newSerial).isEqualTo(oldSerial);
        assertThatCert(secondCert)
            .doesNotHaveContentRepoType(content1)
            .hasContentRepoType(content2)
            .doesNotHaveContentRepoType(content3);
    }

    @Test
    public void shouldRegenWhenRemovingHighPriorityEnvironmentWithUniqueContent() {
        ProductDTO product = ownerClient.ownerProducts()
            .createProductByOwner(owner.getKey(), Products.randomEng());
        addContentToProduct(product, content1);
        addContentToProduct(product, content2);
        addContentToProduct(product, content3);
        promoteContent(env1, content2);
        promoteContent(env2, content3);
        PoolDTO pool = ownerClient.owners().createPool(owner.getKey(), Pools.random(product));

        ConsumerDTO consumer = createConsumer(env1, env2);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        EntitlementDTO entitlement = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 1).get(0);
        CertificateDTO firstCert = findFirstCert(entitlement);
        Long oldSerial = firstCert.getSerial().getSerial();

        assertThatCert(firstCert)
            .doesNotHaveContentRepoType(content1)
            .hasContentRepoType(content2)
            .hasContentRepoType(content3);

        ownerClient.environments().deleteEnvironment(env1.getId());

        List<EntitlementDTO> entsWithSecondEnvironment = ownerClient.consumers()
            .listEntitlementsWithRegen(consumer.getUuid());
        CertificateDTO secondCert = findFirstCert(entsWithSecondEnvironment);
        Long newSerial = secondCert.getSerial().getSerial();

        assertThat(newSerial).isNotEqualTo(oldSerial);
        assertThatCert(secondCert)
            .doesNotHaveContentRepoType(content1)
            .doesNotHaveContentRepoType(content2)
            .hasContentRepoType(content3);
    }

    @Test
    public void shouldNotRegenWhenRemovingEnvironmentWithContentNotProvidedByEntitlement() {
        ProductDTO product = ownerClient.ownerProducts()
            .createProductByOwner(owner.getKey(), Products.randomEng());
        ContentDTO content4 = createContent();
        addContentToProduct(product, content1);
        addContentToProduct(product, content2);
        promoteContent(env1, content3);
        promoteContent(env2, content4);
        PoolDTO pool = ownerClient.owners().createPool(owner.getKey(), Pools.random(product));

        ConsumerDTO consumer = createConsumer(env1, env2);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        EntitlementDTO entitlement = consumerClient.consumers()
            .bindPoolSync(consumer.getUuid(), pool.getId(), 1).get(0);
        CertificateDTO firstCert = findFirstCert(entitlement);
        Long oldSerial = firstCert.getSerial().getSerial();

        assertThatCert(firstCert)
            .doesNotHaveContentRepoType(content1)
            .doesNotHaveContentRepoType(content2)
            .doesNotHaveContentRepoType(content3)
            .doesNotHaveContentRepoType(content4);

        ownerClient.environments().deleteEnvironment(env1.getId());

        List<EntitlementDTO> entsWithSecondEnvironment = ownerClient.consumers()
            .listEntitlementsWithRegen(consumer.getUuid());
        CertificateDTO secondCert = findFirstCert(entsWithSecondEnvironment);
        Long newSerial = secondCert.getSerial().getSerial();

        assertThat(newSerial).isEqualTo(oldSerial);
        assertThatCert(secondCert)
            .doesNotHaveContentRepoType(content1)
            .doesNotHaveContentRepoType(content2)
            .doesNotHaveContentRepoType(content3)
            .doesNotHaveContentRepoType(content4);
    }

    private ConsumerDTO createConsumer(EnvironmentDTO... environments) {
        return ownerClient.consumers().createConsumer(Consumers.random(owner)
            .putFactsItem(Facts.CertificateVersion.key(), "1.0")
            .environments(Arrays.asList(environments)));
    }

    private void addContentToProduct(ProductDTO product, ContentDTO content1) {
        ownerClient.ownerProducts().addContent(owner.getKey(), product.getId(), content1.getId(), true);
    }

    private ContentDTO createContent() {
        return ownerClient.ownerContent().createContent(owner.getKey(), Contents.random().arches("x86_64"));
    }

    private CertificateDTO findFirstCert(List<EntitlementDTO> entitlements) {
        return entitlements.stream()
            .map(EntitlementDTO::getCertificates)
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .findFirst()
            .orElseThrow();
    }

    private CertificateDTO findFirstCert(EntitlementDTO entitlement) {
        return entitlement.getCertificates().stream()
            .findFirst()
            .orElseThrow();
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

    private ContentToPromoteDTO toPromote(ContentDTO content) {
        return new ContentToPromoteDTO()
            .contentId(content.getId())
            .enabled(true);
    }

}
