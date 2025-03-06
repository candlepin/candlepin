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
package org.candlepin.pki.certs;

import org.candlepin.model.Consumer;
import org.candlepin.model.ContentAccessPayload;
import org.candlepin.model.Environment;
import org.candlepin.util.Util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Builder class responsible for generating a key for a {@link ContentAccessPayload}.
 */
public class ContentAccessPayloadKeyBuilder {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int SCA_CONTENT_ACCESS_PAYLOAD_KEY_VERSION = 1;
    private static final HexFormat HEX_FORMATTER = HexFormat.of();

    private Consumer consumer;
    private List<Environment> environments = List.of();

    /**
     * Creates a new content access payload builder instance for the provided consumer.
     *
     * @param consumer
     *  the consumer to build a content access payload builder instance for
     *
     * @throws IllegalArgumentException
     *  if the provided consumer is null
     */
    public ContentAccessPayloadKeyBuilder(Consumer consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("consumer is null");
        }

        this.consumer = consumer;
    }

    /**
     * Sets the environments used to generate the content access payload key. If a null value is provided then
     * no environments will be used.
     *
     * @param environments
     *  the environments used to generate the content access payload key
     *
     * @return this instance of ContentAccessPayloadKeyBuilder
     */
    public ContentAccessPayloadKeyBuilder setEnvironments(List<Environment> environments) {
        this.environments = environments == null ? List.of() : environments;
        return this;
    }

    /**
     * Generates and returns a key for a {@link ContentAccessPayload}.
     *
     * @return a generated content access payload key
     */
    public String build() {
        String input = new StringBuilder("arches:")
            .append(getNormalizedArchitectures(consumer))
            .append("-environments:")
            .append(getNormalizedEnvironments(environments))
            .toString();

        try {
            byte[] hash = MessageDigest.getInstance(HASH_ALGORITHM)
                .digest(input.getBytes(StandardCharsets.UTF_8));

            return new StringBuilder("v")
                .append(SCA_CONTENT_ACCESS_PAYLOAD_KEY_VERSION)
                .append(":")
                .append(HEX_FORMATTER.formatHex(hash))
                .toString();
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private String getNormalizedArchitectures(Consumer consumer) {
        SortedSet<String> sorted = new TreeSet<>();

        String arch = consumer.getFact(Consumer.Facts.ARCHITECTURE);
        if (arch != null) {
            sorted.add(arch.strip().toLowerCase());
        }

        String supportedArches = consumer.getFact(Consumer.Facts.SUPPORTED_ARCHITECTURES);
        if (supportedArches != null) {
            List<String> sarches = Util.toList(supportedArches.strip().toLowerCase());
            sorted.addAll(sarches);
        }

        return String.join(",", sorted);
    }

    private String getNormalizedEnvironments(List<Environment> environments) {
        return environments.stream()
            .map(Environment::getId)
            .distinct()
            .collect(Collectors.joining(","));
    }

}
