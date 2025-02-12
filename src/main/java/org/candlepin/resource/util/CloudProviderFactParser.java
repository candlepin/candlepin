/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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

package org.candlepin.resource.util;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Interface for parsing cloud-specific consumer facts.
 * <p>
 * Implementations of this interface extract cloud-related metadata from a given map of facts.
 * This is used to identify the cloud provider and retrieve relevant identifiers such as account ID,
 * offering IDs, or instance ID.
 * </p>
 *
 * <p>
 * Each implementation is responsible for determining whether it supports a given set of facts
 * (e.g., whether the facts belong to AWS, Azure, or GCP).
 * </p>
 */
public interface CloudProviderFactParser {

    /**
     * Attempts to extract the cloud account ID from the given facts.
     *
     * @param facts a map of key-value pairs representing consumer facts
     * @return an {@code Optional} containing the account ID if present and recognized, or empty otherwise
     */
    Optional<String> getAccountId(Map<String, String> facts);

    /**
     * Attempts to extract one or more offering IDs from the given facts.
     *
     * @param facts a map of key-value pairs representing consumer facts
     * @return an {@code Optional} containing a list of offering IDs if present and recognized,
     *          or empty otherwise
     */
    Optional<List<String>> getOfferingIds(Map<String, String> facts);

    /**
     * Attempts to extract the cloud instance ID from the given facts.
     *
     * @param facts a map of key-value pairs representing consumer facts
     * @return an {@code Optional} containing the instance ID if present and recognized, or empty otherwise
     */
    Optional<String> getInstanceId(Map<String, String> facts);

    /**
     * Returns a short, unique identifier for the cloud provider (e.g., "aws", "azure", "gcp").
     *
     * @return the short name of the cloud provider
     */
    String getShortName();

    /**
     * Determines whether this parser supports the given set of facts.
     * Used to identify if the facts belong to the specific cloud provider.
     *
     * @param facts a map of key-value pairs representing consumer facts
     * @return {@code true} if the facts match this cloud provider, {@code false} otherwise
     */
    boolean isSupported(Map<String, String> facts);
}
