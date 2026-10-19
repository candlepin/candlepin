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

import org.candlepin.config.Configuration;
import org.candlepin.controller.util.ContentPathBuilder;
import org.candlepin.controller.util.PromotedContent;
import org.candlepin.model.Consumer;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.util.Arch;
import org.candlepin.util.X509V3ExtensionUtil;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Gathering, filtering, and mapping of repositories for SCA payloads
 */
@Singleton
public class ScaContentDataProvider {
    private final Configuration configuration;
    private final ContentCurator contentCurator;
    private final X509V3ExtensionUtil extensionUtil;

    @Inject
    public ScaContentDataProvider(Configuration configuration, ContentCurator contentCurator,
        X509V3ExtensionUtil extensionUtil) {

        this.configuration = Objects.requireNonNull(configuration);
        this.contentCurator = Objects.requireNonNull(contentCurator);
        this.extensionUtil = Objects.requireNonNull(extensionUtil);
    }

    /**
     * Gathers active owner content and prepares immutable payload data
     *
     * @return filtered payload data
     */
    /* pkg-private */ ScaPayloadData gather(Owner owner, Consumer consumer, List<Environment> environments,
        Instant timestamp) {

        Objects.requireNonNull(owner);
        Objects.requireNonNull(consumer);
        Objects.requireNonNull(environments);
        Objects.requireNonNull(timestamp);

        return this.gather(owner, consumer, environments, timestamp, new ExportScaContentStrategy());
    }

    /**
     * Gathers repositories using the selected policy
     *
     * @return detached payload data
     */
    /* pkg-private */ ScaPayloadData gather(Owner owner, Consumer consumer, List<Environment> environments,
        Instant timestamp, ScaContentStrategy strategy) {

        Objects.requireNonNull(owner);
        Objects.requireNonNull(consumer);
        Objects.requireNonNull(environments);
        Objects.requireNonNull(timestamp);
        Objects.requireNonNull(strategy);
        return this.prepare(owner, consumer, environments, timestamp,
            strategy.gather(this.contentCurator, Objects.requireNonNull(owner).getId()), strategy);
    }

    /**
     * Filters supplied content without querying the database or performing cryptography
     *
     * @return immutable payload data detached from the supplied entities
     */
    /* pkg-private */ ScaPayloadData prepare(Owner owner, Consumer consumer, List<Environment> environments,
        Instant timestamp, Collection<ProductContent> content) {

        return this.prepare(owner, consumer, environments, timestamp, content, new ExportScaContentStrategy());
    }

    /**
     * Applies a selection policy before merging and mapping repositories
     *
     * @return detached payload data
     */
    /* pkg-private */ ScaPayloadData prepare(Owner owner, Consumer consumer, List<Environment> environments,
        Instant timestamp, Collection<ProductContent> content, ScaContentStrategy strategy) {

        Objects.requireNonNull(owner);
        Objects.requireNonNull(consumer);
        Objects.requireNonNull(environments);
        Objects.requireNonNull(timestamp);
        Objects.requireNonNull(content);

        Objects.requireNonNull(strategy);
        environments = strategy.environments(environments);
        PromotedContent promotedContent = new PromotedContent(ContentPathBuilder.from(owner, environments))
            .withAll(environments);
        Map<String, ProductContent> merged = content.stream()
            .filter(strategy::includes)
            .collect(Collectors.toMap(pc -> pc.getContent().getId(), Function.identity(),
                (first, second) -> new ProductContent(second.getContent(), first.isEnabled() || second.isEnabled())));

        Product product = new Product()
            .setId("content_access")
            .setName(" Content Access")
            .setProductContent(merged.values());
        boolean filterEnvironments = strategy.filtersEnvironments(this.configuration);
        Set<ProductContent> filtered = strategy.filter(product, consumer, promotedContent,
            this.configuration, this.extensionUtil);

        List<ScaContentData> repositories = new ArrayList<>();
        for (ProductContent pc : filtered) {
            repositories.add(this.mapContent(pc, promotedContent, consumer, filterEnvironments));
        }

        return new ScaPayloadData(consumer.getUuid(), timestamp, repositories);
    }

    /**
     * Checks whether a cached ExportV2 payload may contain repositories from expired pools
     *
     * @return whether a pool ended after the payload was prepared
     */
    /* pkg-private */ boolean hasExpiredPoolsSince(Owner owner, Date timestamp) {
        return this.contentCurator.hasPoolsExpiredSince(owner.getId(), timestamp);
    }

    private ScaContentData mapContent(ProductContent pc, PromotedContent promotedContent, Consumer consumer,
        boolean filterEnvironments) {

        Content content = pc.getContent();
        boolean enabled = pc.isEnabled();
        if (filterEnvironments && !consumer.getEnvironmentIds().isEmpty()) {
            Boolean override = promotedContent.isEnabled(pc);
            if (override != null) {
                enabled = override;
            }
        }

        List<String> tags = null;
        if (content.getRequiredTags() != null && !content.getRequiredTags().isEmpty()) {
            tags = new ArrayList<>();
            StringTokenizer tokenizer = new StringTokenizer(content.getRequiredTags(), ",");
            while (tokenizer.hasMoreTokens()) {
                tags.add(tokenizer.nextToken());
            }
        }

        return new ScaContentData(content.getId(), content.getType(), content.getName(), content.getLabel(),
            content.getVendor(), promotedContent.getPath(pc), content.getGpgUrl(), enabled ? null : Boolean.FALSE,
            content.getMetadataExpiration(), tags, new ArrayList<>(Arch.parseArches(content.getArches())));
    }
}
