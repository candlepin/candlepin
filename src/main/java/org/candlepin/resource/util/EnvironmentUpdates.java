/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class represents a collection of environment changes for each affected
 * consumer.
 */
public class EnvironmentUpdates {

    private final Map<String, List<String>> current = new HashMap<>();
    private final Map<String, List<String>> updated = new HashMap<>();

    public void put(String consumerId, List<String> currentEnvs, List<String> updatedEnvs) {
        this.current.put(consumerId, currentEnvs);
        this.updated.put(consumerId, updatedEnvs);
    }

    /**
     * Returns consumers that are path of these updates.
     *
     * @return consumer ids
     */
    public Set<String> consumers() {
        return new HashSet<>(this.current.keySet());
    }

    /**
     * Returns environments that are path of these updates. This includes both
     * environments present before updates and those that will be used after
     * the updates.
     *
     * @return environment ids
     */
    public Set<String> environments() {
        return Stream.of(this.current.values(), this.updated.values())
            .flatMap(Collection::stream)
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    }

    /**
     * Environments currently in use by the given consumer.
     *
     * @param consumerId an id of the consumer for whom to retrieve the current environments.
     * @return environment ids
     */
    public List<String> currentEnvsOf(String consumerId) {
        return new ArrayList<>(this.current.get(consumerId));
    }

    /**
     * Environments that will be in use by the given consumer after the update.
     *
     * @param consumerId an id of the consumer for whom to retrieve the updated environments.
     * @return environment ids
     */
    public List<String> updatedEnvsOf(String consumerId) {
        return new ArrayList<>(this.updated.get(consumerId));
    }
}
