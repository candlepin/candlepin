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
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.pki.DistinguishedName;
import org.candlepin.util.Util;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Gathering and preparation of SCA certificate subjects and access paths
 */
@Singleton
public class ScaCertificateDataProvider {
    private final EnvironmentCurator environmentCurator;

    /**
     * Initializes the provider with the curator used to retrieve consumer environments
     *
     * @param environmentCurator
     *     curator supplying consumer environments in priority order
     * @throws NullPointerException
     *     if the curator is null
     */
    @Inject
    public ScaCertificateDataProvider(EnvironmentCurator environmentCurator) {
        this.environmentCurator = Objects.requireNonNull(environmentCurator);
    }

    /**
     * Retrieves the consumer's environments in priority order
     * <p>
     * Returns the curator's result directly, without copying or reordering it.
     *
     * @param consumer
     *     consumer whose assigned environments are retrieved
     * @throws NullPointerException
     *     if the consumer is null
     * @return environments ordered from highest to lowest priority, or an empty list if none are assigned
     */
    public List<Environment> getEnvironments(Consumer consumer) {
        return this.environmentCurator.getConsumerEnvironments(Objects.requireNonNull(consumer));
    }

    /**
     * Prepares an immutable snapshot of the SCA certificate subject and access paths
     * <p>
     * Builds the subject from the consumer UUID and owner key. Creates one access path per supplied
     * environment, preserving list order, or a single owner path when the list is empty. Paths start
     * with {@code /} and combine the owner key with the environment name. Each component is split on
     * {@code /}; blank segments are omitted and the remaining segments are URL-encoded individually.
     * <p>
     * Uses only the supplied consumer and environments, without querying the curator or repository
     * content. Later changes to these inputs do not affect the returned subject or paths.
     *
     * @param consumer
     *     consumer supplying the certificate subject and owner key
     * @param environments
     *     environments to include in access paths, in the desired order; an empty list selects the owner path
     * @throws NullPointerException
     *     if the consumer, its owner, the environment list, or any element of that list is null
     * @throws IllegalArgumentException
     *     if both the consumer UUID and owner key are blank and cannot form a distinguished name
     * @return certificate data containing the subject and an immutable list of encoded access paths
     */
    public ScaCertificateData prepare(Consumer consumer, List<Environment> environments) {
        Objects.requireNonNull(consumer);
        Owner owner = Objects.requireNonNull(consumer.getOwner());
        Objects.requireNonNull(environments);

        List<String> paths = environments.isEmpty() ? List.of(this.buildPath(owner.getKey(), null)) :
            environments.stream()
                .map(environment -> this.buildPath(owner.getKey(), environment.getName()))
                .toList();

        return new ScaCertificateData(new DistinguishedName(consumer.getUuid(), owner), paths);
    }

    private String buildPath(String ownerKey, String environmentName) {
        return Stream.of(ownerKey, environmentName)
            .filter(Objects::nonNull)
            .flatMap(component -> Arrays.stream(component.split("/")))
            .filter(segment -> !segment.isBlank())
            .map(Util::encodeUrl)
            .collect(Collectors.joining("/", "/", ""));
    }
}
