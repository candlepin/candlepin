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

    @Inject
    public ScaCertificateDataProvider(EnvironmentCurator environmentCurator) {
        this.environmentCurator = Objects.requireNonNull(environmentCurator);
    }

    /**
     * Retrieves environments in priority order
     *
     * @return environments ordered from highest to lowest priority
     */
    /* pkg-private */ List<Environment> getEnvironments(Consumer consumer) {
        return this.environmentCurator.getConsumerEnvironments(Objects.requireNonNull(consumer));
    }

    /**
     * Prepares certificate data without accessing repository content
     *
     * @return immutable certificate subject and access paths
     */
    /* pkg-private */ ScaCertificateData prepare(Consumer consumer, List<Environment> environments) {
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
