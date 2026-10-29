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

package org.candlepin.sync;

import org.candlepin.dto.manifest.v2.ConsumerDTO;
import org.candlepin.dto.manifest.v2.MetaDTO;
import org.candlepin.dto.manifest.v2.ProductDTO;
import org.candlepin.dto.manifest.v2.SubscriptionDTO;
import org.candlepin.model.SCACertificate;

import java.nio.file.Path;
import java.util.Map;

/**
 * Data for a single version 2 manifest export
 * <p>
 * Holds the DTOs, simple content access (SCA) certificate, and work directory prepared by
 * {@link ExportDataAggregator} for {@link ExporterV2}.
 * Separates the export payload from the gathering, translation, certificate generation, and archive writing
 * performed by those collaborators. Supplied references are retained without validation or defensive copies.
 * The DTOs and maps are mutable and belong to the current export. Products are keyed by product ID and
 * subscriptions by pool ID. Additional export data can be added as the manifest requirements evolve.
 */
public class ExportData {
    /**
     * Metadata for the current export
     */
    private final MetaDTO metaDTO;
    /**
     * Consumer representation for the current export
     */
    private final ConsumerDTO consumerDTO;
    /**
     * Products keyed by product ID
     */
    private final Map<String, ProductDTO> products;
    /**
     * Subscriptions keyed by pool ID
     */
    private final Map<String, SubscriptionDTO> subscriptions;
    /**
     * Directory captured for archive creation
     */
    private final Path workDirectory;
    /**
     * SCA certificate and private key to include in the archive
     */
    private final SCACertificate scaCertificate;

    /**
     * Stores references to the data prepared for a single export
     *
     * @param metaDTO
     *     export metadata
     * @param consumerDTO
     *     consumer representation
     * @param products
     *     product DTOs keyed by product ID
     * @param subscriptions
     *     subscription DTOs keyed by pool ID
     * @param workDirectory
     *     directory in which to create the archive
     * @param scaCertificate
     *     SCA certificate containing the exported content payload and its private key
     */
    protected ExportData(MetaDTO metaDTO, ConsumerDTO consumerDTO, Map<String, ProductDTO> products,
        Map<String, SubscriptionDTO> subscriptions, Path workDirectory, SCACertificate scaCertificate) {
        this.metaDTO = metaDTO;
        this.consumerDTO = consumerDTO;
        this.products = products;
        this.subscriptions = subscriptions;
        this.workDirectory = workDirectory;
        this.scaCertificate = scaCertificate;
    }

    /**
     * SCA certificate prepared from the exported product content
     *
     * @return the supplied certificate, or null if none was supplied
     */
    public SCACertificate getScaCertificate() {
        return this.scaCertificate;
    }

    /**
     * Consumer representation for serialization
     *
     * @return the mutable consumer DTO held by this instance
     */
    public ConsumerDTO getConsumerDTO() {
        return this.consumerDTO;
    }

    /**
     * Products collected for this export
     *
     * @return the live, mutable map of product IDs to product DTOs; empty when no products were resolved
     */
    public Map<String, ProductDTO> getProducts() {
        return this.products;
    }

    /**
     * Subscriptions collected from eligible owner pools
     *
     * @return the live, mutable map of pool IDs to subscription DTOs; empty when no pools were found
     */
    public Map<String, SubscriptionDTO> getSubscriptions() {
        return this.subscriptions;
    }

    /**
     * Export metadata, including creation time, creator, Candlepin version, and manifest format version
     * <p>
     * The aggregator supplies the Candlepin version and release in {@code version} and {@code 2.0.0} in
     * {@code manifestVersion}.
     *
     * @return the mutable metadata DTO held by this instance
     */
    public MetaDTO getMetaDTO() {
        return this.metaDTO;
    }

    /**
     * Configured directory for creating the export archive
     * <p>
     * Captured by the aggregator without creating or validating the directory.
     *
     * @return the path configured by the aggregator
     */
    public Path getWorkDirectory() {
        return this.workDirectory;
    }
}
