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

import org.candlepin.dto.manifest.v1.ConsumerDTO;
import org.candlepin.dto.manifest.v1.SubscriptionDTO;

import java.util.List;
import java.util.Objects;

/**
 * Detached, validated v2 payload adapted to the existing consumer and subscription import models
 * <p>
 * Certificates are deliberately excluded and must be obtained through the verified certificate integrations.
 */
public class ManifestV2Data {
    private final Meta meta;
    private final ConsumerDTO consumer;
    private final List<SubscriptionDTO> subscriptions;

    /**
     * Initializes detached import data
     *
     * @param meta
     *     the producer metadata
     * @param consumer
     *     the upstream consumer in SCA mode
     * @param subscriptions
     *     subscriptions with unlimited quantities and upstream pool identities
     */
    public ManifestV2Data(Meta meta, ConsumerDTO consumer, List<SubscriptionDTO> subscriptions) {
        this.meta = Objects.requireNonNull(meta);
        this.consumer = Objects.requireNonNull(consumer);
        this.subscriptions = List.copyOf(subscriptions);
    }

    /**
     * Retrieves producer metadata
     *
     * @return the metadata used for conflict validation and import records
     */
    public Meta getMeta() {
        return this.meta;
    }

    /**
     * Retrieves the upstream consumer
     *
     * @return the consumer metadata, without certificate material
     */
    public ConsumerDTO getConsumer() {
        return this.consumer;
    }

    /**
     * Retrieves subscriptions for reconciliation and pool refresh
     *
     * @return an unmodifiable list of mutable subscription import models
     */
    public List<SubscriptionDTO> getSubscriptions() {
        return this.subscriptions;
    }
}
