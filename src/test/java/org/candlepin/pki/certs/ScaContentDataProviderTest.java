/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.pki.certs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.controller.ContentAccessMode;
import org.candlepin.model.Consumer;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.Owner;
import org.candlepin.model.ProductContent;
import org.candlepin.pki.huffman.Huffman;
import org.candlepin.util.X509V3ExtensionUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.List;
import java.util.Set;

class ScaContentDataProviderTest {
    private ScaContentDataProvider createProvider(Configuration config, ContentCurator curator) {
        return new ScaContentDataProvider(config, curator,
            new X509V3ExtensionUtil(config, mock(EntitlementCurator.class), new Huffman()));
    }

    private Owner createOwner() {
        return new Owner().setId("owner").setKey("org")
            .setContentAccessMode(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());
    }

    private Content createContent(String id) {
        return new Content(id)
            .setName("Repository")
            .setType("yum")
            .setLabel(id)
            .setContentUrl("/content/$basearch")
            .setGpgUrl("https://example.test/key");
    }

    @Test
    void preparesDetachedMetadataAndMergesEnabledFlags() {
        DevConfig config = TestConfig.defaults();
        ContentCurator curator = mock(ContentCurator.class);
        Owner owner = this.createOwner();
        Consumer consumer = new Consumer().setUuid("consumer").setOwner(owner);
        Content content = this.createContent("repo")
            .setArches("x86_64, x86_64")
            .setMetadataExpiration(600L)
            .setRequiredTags("first,, second")
            .setModifiedProductIds(Set.of("unentitled"));

        ScaPayloadData data = this.createProvider(config, curator).prepare(owner, consumer, List.of(),
            Instant.EPOCH, List.of(new ProductContent(content, false), new ProductContent(content, true)));
        content.setName("changed").setArches("aarch64").setRequiredTags("changed");

        assertNotNull(data.content());
        assertThat(data.content()).hasSize(1);
        ScaContentData repository = data.content().getFirst();
        assertThat(repository.name()).isEqualTo("Repository");
        assertThat(repository.enabled()).isNull();
        assertThat(repository.arches()).containsExactly("x86_64");
        assertThat(repository.requiredTags()).containsExactly("first", " second");
        assertThat(repository.metadataExpiration()).isEqualTo(600L);
        assertThat(repository.path()).isEqualTo("/content/$basearch");
        assertThrows(UnsupportedOperationException.class, () -> data.content().clear());
        assertThrows(UnsupportedOperationException.class, () -> repository.arches().clear());
        assertThrows(UnsupportedOperationException.class, () -> repository.requiredTags().clear());
        verifyNoInteractions(curator);
    }

    @ParameterizedTest
    @CsvSource({"x86_64,x86_64,true", "aarch64,x86_64,false", "ALL,x86_64,true",
        "noarch,x86_64,true", "x86,i686,true", "x86,x86_64,false", "X86_64,x86_64,false"})
    void filtersArchitectures(String contentArch, String consumerArch, boolean included) {
        Owner owner = this.createOwner();
        Consumer consumer = new Consumer().setUuid("consumer").setOwner(owner)
            .setFact(Consumer.Facts.ARCHITECTURE, consumerArch);
        ProductContent content = new ProductContent(this.createContent("repo").setArches(contentArch), true);
        ScaPayloadData data = this.createProvider(TestConfig.defaults(), mock(ContentCurator.class))
            .prepare(owner, consumer, List.of(), Instant.EPOCH, List.of(content));

        assertThat(data.content()).hasSize(included ? 1 : 0);
    }

    @Test
    void appliesPromotionPriorityAndRetainsDisabledRepositories() {
        DevConfig config = TestConfig.defaults();
        config.setProperty(ConfigProperties.ENV_CONTENT_FILTERING, "true");
        Owner owner = this.createOwner();
        Consumer consumer = new Consumer().setUuid("consumer").setOwner(owner)
            .setEnvironmentIds(List.of("first", "second"));
        Content repository = this.createContent("repo");
        Environment first = new Environment().setId("first").setName("first").setContentPrefix("/first");
        Environment second = new Environment().setId("second").setName("second").setContentPrefix("/second");
        first.addEnvironmentContent(new EnvironmentContent().setEnvironment(first).setContent(repository).setEnabled(false));
        second.addEnvironmentContent(new EnvironmentContent().setEnvironment(second).setContent(repository).setEnabled(true));

        ScaPayloadData data = this.createProvider(config, mock(ContentCurator.class)).prepare(owner, consumer,
            List.of(first, second), Instant.EPOCH, List.of(new ProductContent(repository, true),
                new ProductContent(this.createContent("unpromoted"), true)));

        assertThat(data.content()).hasSize(1);
        assertThat(data.content().getFirst().enabled()).isFalse();
        assertThat(data.content().getFirst().path()).isEqualTo("/first/content/$basearch");
    }

    @Test
    void gathersActiveContentThroughCurator() {
        ContentCurator curator = mock(ContentCurator.class);
        Owner owner = this.createOwner().setId("owner-id");
        Consumer consumer = new Consumer().setUuid("consumer").setOwner(owner);
        when(curator.getActiveContentByOwner(owner.getId())).thenReturn(List.of());

        ScaPayloadData data = this.createProvider(TestConfig.defaults(), curator)
            .gather(owner, consumer, List.of(), Instant.EPOCH);

        assertThat(data.content()).isEmpty();
        verify(curator).getActiveContentByOwner("owner-id");
    }

    @Test
    void exportV2IgnoresConsumerFiltersAndRejectsLocalContentBeforeMerging() {
        DevConfig config = TestConfig.defaults();
        config.setProperty(ConfigProperties.ENV_CONTENT_FILTERING, "true");
        Owner owner = this.createOwner().setContentPrefix("/owner");
        Consumer consumer = new Consumer().setUuid("consumer").setOwner(owner)
            .setFact(Consumer.Facts.ARCHITECTURE, "x86_64")
            .setEnvironmentIds(List.of("environment"));
        Content global = this.createContent("repo").setArches("aarch64").setRequiredTags("tag");
        Content local = this.createContent("repo").setNamespace(owner.getKey()).setName("Local");
        Environment environment = new Environment().setId("environment").setName("env")
            .setContentPrefix("/environment");
        environment.addEnvironmentContent(new EnvironmentContent().setEnvironment(environment)
            .setContent(global).setEnabled(true));

        ScaPayloadData data = this.createProvider(config, mock(ContentCurator.class)).prepare(owner, consumer,
            List.of(environment), Instant.EPOCH, List.of(new ProductContent(global, false),
                new ProductContent(local, true), new ProductContent(this.createContent("unpromoted"), true)),
            new ExportV2ScaContentStrategy());

        assertThat(data.content()).hasSize(2);
        ScaContentData repository = data.content().stream().filter(entry -> entry.id().equals("repo"))
            .findFirst().orElseThrow();
        assertThat(repository.name()).isEqualTo("Repository");
        assertThat(repository.enabled()).isFalse();
        assertThat(repository.path()).isEqualTo("/owner/content/$basearch");
        assertThat(repository.arches()).containsExactly("aarch64");
        assertThat(repository.requiredTags()).containsExactly("tag");
    }

    @Test
    void exportV2GathersFutureGlobalContentThroughCurator() {
        ContentCurator curator = mock(ContentCurator.class);
        Owner owner = this.createOwner();
        Consumer consumer = new Consumer().setUuid("consumer").setOwner(owner);
        when(curator.getExportV2ContentByOwner(owner.getId())).thenReturn(List.of());

        ScaPayloadData data = this.createProvider(TestConfig.defaults(), curator)
            .gather(owner, consumer, List.of(), Instant.EPOCH, new ExportV2ScaContentStrategy());

        assertThat(data.content()).isEmpty();
        verify(curator).getExportV2ContentByOwner(owner.getId());
    }

    @Test
    void rejectsMissingAndMalformedInputs() {
        ScaContentDataProvider provider = this.createProvider(TestConfig.defaults(), mock(ContentCurator.class));
        Owner owner = this.createOwner();
        Consumer consumer = new Consumer().setUuid("consumer").setOwner(owner);

        assertThrows(NullPointerException.class, () -> provider.prepare(null, consumer, List.of(), Instant.EPOCH, List.of()));
        assertThrows(NullPointerException.class, () -> provider.prepare(owner, null, List.of(), Instant.EPOCH, List.of()));
        assertThrows(NullPointerException.class, () -> provider.prepare(owner, consumer, null, Instant.EPOCH, List.of()));
        assertThrows(NullPointerException.class, () -> provider.prepare(owner, consumer, List.of(), null, List.of()));
        assertThrows(NullPointerException.class, () -> provider.prepare(owner, consumer, List.of(), Instant.EPOCH, null));
        Content invalid = this.createContent("invalid").setContentUrl("");
        assertThrows(IllegalArgumentException.class, () -> provider.prepare(owner, consumer, List.of(),
            Instant.EPOCH, List.of(new ProductContent(invalid, true))));
    }
}
