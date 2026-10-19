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

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.controller.util.PromotedContent;
import org.candlepin.model.Consumer;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.util.X509V3ExtensionUtil;

import java.util.List;
import java.util.Set;

/**
 * Existing active-pool, architecture, and environment selection policy
 */
final class ExportScaContentStrategy implements ScaContentStrategy {
    @Override
    public String cacheId() {
        return "export:1";
    }

    @Override
    public boolean usesConsumerContext() {
        return true;
    }

    @Override
    public List<ProductContent> gather(ContentCurator curator, String ownerId) {
        return curator.getActiveContentByOwner(ownerId);
    }

    @Override
    public boolean includes(ProductContent content) {
        return true;
    }

    @Override
    public List<Environment> environments(List<Environment> environments) {
        return environments;
    }

    @Override
    public boolean filtersEnvironments(Configuration configuration) {
        return configuration.getBoolean(ConfigProperties.ENV_CONTENT_FILTERING);
    }

    @Override
    public Set<ProductContent> filter(Product product, Consumer consumer, PromotedContent promotedContent,
        Configuration configuration, X509V3ExtensionUtil extensionUtil) {

        Set<ProductContent> filtered = extensionUtil.filterProductContent(product, consumer, promotedContent,
            this.filtersEnvironments(configuration), Set.of("content-access"),
            consumer.getOwner().isUsingSimpleContentAccess());
        return extensionUtil.filterContentByContentArch(filtered, consumer, product);
    }
}
