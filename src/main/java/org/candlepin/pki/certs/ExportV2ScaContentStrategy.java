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
 * Global content from active and future pools, without consumer-specific filtering
 */
final class ExportV2ScaContentStrategy implements ScaContentStrategy {
    @Override
    public String cacheId() {
        return "export-v2:1";
    }

    @Override
    public boolean usesConsumerContext() {
        return false;
    }

    @Override
    public List<ProductContent> gather(ContentCurator curator, String ownerId) {
        return curator.getExportV2ContentByOwner(ownerId);
    }

    @Override
    public boolean includes(ProductContent content) {
        String namespace = content.getContent().getNamespace();
        return namespace == null || namespace.isEmpty();
    }

    @Override
    public List<Environment> environments(List<Environment> environments) {
        return List.of();
    }

    @Override
    public boolean filtersEnvironments(Configuration configuration) {
        return false;
    }

    @Override
    public Set<ProductContent> filter(Product product, Consumer consumer, PromotedContent promotedContent,
        Configuration configuration, X509V3ExtensionUtil extensionUtil) {

        return Set.copyOf(product.getProductContent());
    }
}
