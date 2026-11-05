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

import org.candlepin.model.Consumer;
import org.candlepin.model.ContentAccessPayload;
import org.candlepin.model.ContentAccessPayloadCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.Owner;
import org.candlepin.model.ProductContent;
import org.candlepin.pki.Scheme;
import org.candlepin.util.Util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.persistence.PersistenceException;



/**
 * Builder class responsible for generating or fetching {@link ContentAccessPayload} instances.
 */
public class ContentAccessPayloadBuilder {
    private static final Logger log = LoggerFactory.getLogger(ContentAccessPayloadBuilder.class);

    private static final int CONTENT_ACCESS_PAYLOAD_KEY_VERSION = 3;
    private static final String KEY_HASH_ALGORITHM = "SHA-256";
    private static final HexFormat HEX_FORMATTER = HexFormat.of();

    private final ScaContentDataProvider dataProvider;
    private final ScaPayloadEncoder encoder;
    private final ContentAccessPayloadCurator contentAccessPayloadCurator;

    // Collected builder state
    private Owner owner;
    private Consumer consumer;
    private List<Environment> environments;
    private Scheme scheme;
    private List<ProductContent> exportContent;

    public ContentAccessPayloadBuilder(ScaContentDataProvider dataProvider, ScaPayloadEncoder encoder,
        ContentAccessPayloadCurator contentAccessPayloadCurator) {

        this.dataProvider = Objects.requireNonNull(dataProvider);
        this.encoder = Objects.requireNonNull(encoder);
        this.contentAccessPayloadCurator = Objects.requireNonNull(contentAccessPayloadCurator);
    }

    /**
     * Selects the supplied content for an uncached Export V2 payload
     *
     * @param content
     *     the content links selected for the product export; cannot be null
     * @return this builder
     */
    public ContentAccessPayloadBuilder forExportV2(Collection<ProductContent> content) {
        this.exportContent = List.copyOf(content);
        return this;
    }

    private String getNormalizedArchitectures() {
        SortedSet<String> sorted = new TreeSet<>();

        String arch = this.consumer.getFact(Consumer.Facts.ARCHITECTURE);
        if (arch != null) {
            sorted.add(arch.strip().toLowerCase());
        }

        String supportedArches = this.consumer.getFact(Consumer.Facts.SUPPORTED_ARCHITECTURES);
        if (supportedArches != null) {
            List<String> sarches = Util.toList(supportedArches.strip().toLowerCase());
            sorted.addAll(sarches);
        }

        return String.join(",", sorted);
    }

    private String getNormalizedEnvironmentIds() {
        return Optional.ofNullable(this.environments)
            .orElseGet(List::of)
            .stream()
            .map(Environment::getId)
            .distinct()
            .collect(Collectors.joining(","));
    }

    /**
     * Builds a payload key from the current state of this payload builder. Must only be called with a valid
     * payload state.
     *
     * @return
     *  a content access payload key
     */
    private String generatePayloadKey() {
        String input = new StringBuilder("strategy:")
            .append("export:1")
            .append("-arches:")
            .append(this.getNormalizedArchitectures())
            .append("-environments:")
            .append(this.getNormalizedEnvironmentIds())
            .append("-signatureAlgorithm:")
            .append(this.scheme.signatureAlgorithm())
            .toString();

        try {
            byte[] hash = MessageDigest.getInstance(KEY_HASH_ALGORITHM)
                .digest(input.getBytes(StandardCharsets.UTF_8));

            return new StringBuilder("v")
                .append(CONTENT_ACCESS_PAYLOAD_KEY_VERSION)
                .append(":")
                .append(HEX_FORMATTER.formatHex(hash))
                .toString();
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean hasPayloadExpired(ContentAccessPayload payload) {
        Date payloadTimestamp = payload.getTimestamp();

        Stream<Date> ownerContentUpdate = Stream.of(this.owner.getLastContentUpdate());
        Stream<Date> envContentUpdates = Optional.ofNullable(this.environments).orElseGet(List::of)
            .stream()
            .map(Environment::getLastContentUpdate);

        return Stream.concat(ownerContentUpdate, envContentUpdates)
            .anyMatch(contentUpdate -> contentUpdate.after(payloadTimestamp));
    }

    /**
     * Sets the crypto scheme to use to sign the payload built by this builder. If the given scheme is null,
     * this method throws an exception.
     *
     * @param scheme
     *  the crypto scheme to use to sign the content access payload; cannot be null
     *
     * @throws IllegalArgumentException
     *  if the given scheme is null
     *
     * @return
     *  a reference to this builder
     */
    public ContentAccessPayloadBuilder setCryptoScheme(Scheme scheme) {
        if (scheme == null) {
            throw new IllegalArgumentException("scheme is null");
        }

        this.scheme = scheme;
        return this;
    }

    /**
     * Sets the organization which will own the payload built by this builder. If the given owner is null,
     * this method throws an exception.
     *
     * @param owner
     *  the organization to own the payload; cannot be null
     *
     * @throws IllegalArgumentException
     *  if the given owner is null
     *
     * @return
     *  a reference to this builder
     */
    public ContentAccessPayloadBuilder setOwner(Owner owner) {
        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        this.owner = owner;
        return this;
    }

    /**
     * Sets the consumer for which the content access payload will be built. If the given consumer is null,
     * this method throws an exception.
     *
     * @param consumer
     *  the consumer for which the content access payload is built; cannot be null
     *
     * @throws IllegalArgumentException
     *  if the given consumer is null
     *
     * @return
     *  a reference to this builder
     */
    public ContentAccessPayloadBuilder setConsumer(Consumer consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("consumer is null");
        }

        this.consumer = consumer;
        return this;
    }

    /**
     * Sets the environments for which the content access payload will be built. If the given list of
     * environments is null or empty, the existing list of environments will be cleared.
     *
     * @param environments
     *  the consumer's environments for which the content access payload should be applied
     *
     * @return
     *  a reference to this builder
     */
    public ContentAccessPayloadBuilder setEnvironments(List<Environment> environments) {
        this.environments = environments;
        return this;
    }

    /**
     * Builds or fetches a content access payload appropriate for the given crypto scheme, owner, consumer,
     * and environments. If a matching payload does not already exist, a new one will be created, signed, and
     * stored in the database. Otherwise, the existing payload will be returned. If the crypto scheme, owner,
     * or consumer have not yet been set, this method throws an exception.
     * <p>
     * This method may be called multiple times, and will generate the same payload if the state of the
     * organization, consumer, and environments does not change between invocations. Export V2 payloads
     * always use their supplied content and are neither read from nor written to the cache.
     *
     * @throws IllegalStateException
     *  if the crypto scheme, owner, or consumer has not yet been set
     *
     * @throws ConcurrentContentPayloadCreationException
     *  if multiple instances of the content access payload are created at the same time
     *
     * @return
     *  a content access payload for the provided owner, consumer, and environments; signed by the given
     *  crypto scheme
     */
    public ContentAccessPayload build() throws ConcurrentContentPayloadCreationException {
        if (this.scheme == null) {
            throw new IllegalStateException("scheme has not been set");
        }

        if (this.owner == null) {
            throw new IllegalStateException("owner has not been set");
        }

        if (this.consumer == null) {
            throw new IllegalStateException("consumer has not been set");
        }

        if (this.exportContent != null) {
            Date timestamp = new Date();
            ScaPayloadData data = this.dataProvider.prepareForExport(this.owner, this.consumer,
                timestamp.toInstant(), this.exportContent);
            return new ContentAccessPayload()
                .setOwner(this.owner)
                .setTimestamp(timestamp)
                .setPayload(this.encoder.encode(data, this.scheme));
        }

        String payloadKey = this.generatePayloadKey();

        ContentAccessPayload payload = this.contentAccessPayloadCurator
            .getContentAccessPayload(this.owner.getId(), payloadKey);

        if (payload == null || this.hasPayloadExpired(payload)) {
            log.info("Building content access payload for: {}, {}", owner.getKey(), payloadKey);

            Date timestamp = new Date();
            List<Environment> selectedEnvironments = Optional.ofNullable(this.environments).orElseGet(List::of);
            ScaPayloadData data = this.dataProvider.gather(this.owner, this.consumer, selectedEnvironments,
                timestamp.toInstant());
            String payloadData = this.encoder.encode(data, this.scheme);

            if (payload == null) {
                payload = new ContentAccessPayload()
                    .setOwner(this.owner)
                    .setPayloadKey(payloadKey)
                    .setTimestamp(timestamp)
                    .setPayload(payloadData);

                try {
                    this.contentAccessPayloadCurator.create(payload);
                }
                catch (PersistenceException e) {
                    throw new ConcurrentContentPayloadCreationException(e);
                }
            }
            else {
                payload.setTimestamp(timestamp)
                    .setPayload(payloadData);
            }
        }

        return payload;
    }

}
