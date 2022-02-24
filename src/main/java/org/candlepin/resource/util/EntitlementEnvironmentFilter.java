/**
 * Copyright (c) 2021 - 2021 Red Hat, Inc.
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

import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentContentCurator;
import org.candlepin.util.Util;

import org.apache.commons.collections.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The EntitlementEnvironmentFilter class filters the consumer's entitlement
 * whose entitlement certificates are needed to be re-generated because of changes
 * (addition, removal or re-prioritization) in consumer existing environment.
 * This filtering avoids re-generating all the consumer entitlement certificates.
 */
public class EntitlementEnvironmentFilter {
    private final EntitlementCurator entitlementCurator;
    private final EnvironmentContentCurator environmentContentCurator;

    public EntitlementEnvironmentFilter(
        EntitlementCurator entitlementCurator,
        EnvironmentContentCurator environmentContentCurator) {
        this.entitlementCurator = Objects.requireNonNull(entitlementCurator);
        this.environmentContentCurator = Objects.requireNonNull(environmentContentCurator);
    }

    /**
     * Filters the entitlements whose certificates are needed to be re-generated due to environments
     * being added, removed or re-prioritized for a consumer.
     * Idea is to avoid regenerating all the consumer's entitlements certs & filter the
     * necessary entitlements whose certificates are actually needed to be re-generated.
     *
     * @param updates
     *  A collection of consumers, their original environments and their updated environments
     * @return
     *  List of entitlement IDs selected for certificate regeneration
     */
    public Set<String> filterEntitlements(EnvironmentUpdates updates) {
        Set<String> entitlementsToRegen = new HashSet<>();
        Set<String> consumerIds = updates.consumers();
        Set<String> allEnvIds = updates.environments();

        Map<String, List<String>> mapOfEnvironmentContentUUID = this.environmentContentCurator
            .getEnvironmentContentUUIDs(allEnvIds);
        Map<String, Set<String>> mapOfEntitlementContentUUID = this.entitlementCurator
            .getEntitlementContentUUIDs(consumerIds);

        for (String consumerId : consumerIds) {
            List<String> updatedEnvs = updates.currentEnvsOf(consumerId);
            List<String> preExistingEnvs = updates.updatedEnvsOf(consumerId);
            List<String> envRemoved = new ArrayList<>(differenceOf(updatedEnvs, preExistingEnvs));
            Set<String> envChangeList = getEnvironmentChangeList(envRemoved, preExistingEnvs, updatedEnvs);

            for (String entitlementId : mapOfEntitlementContentUUID.keySet()) {
                boolean entToRegen = false;
                Set<String> entitlementContentUUIDs = mapOfEntitlementContentUUID.get(entitlementId);

                for (String environmentId : envChangeList) {
                    List<String> environmentContentUUIDs = mapOfEnvironmentContentUUID.get(environmentId);

                    if (environmentContentUUIDs == null) {
                        // No content present for this env
                        continue;
                    }

                    List<String> currentHigher = new ArrayList<>(
                        findCurrentHigher(updatedEnvs, environmentId));
                    Set<String> currentHigherContentUUIDs = contentOf(
                        mapOfEnvironmentContentUUID, currentHigher);

                    if (isCertRegenerationRequired(environmentContentUUIDs,
                        entitlementContentUUIDs, currentHigherContentUUIDs)) {
                        entToRegen = true;
                        break;
                    }
                }

                // Case where env of higher priority is being removed
                if (!envRemoved.isEmpty() && !entToRegen) {
                    for (String environmentId : envRemoved) {
                        List<String> currentLower = findCurrentLower(
                            envRemoved, preExistingEnvs, environmentId);
                        List<String> contentProvided = mapOfEnvironmentContentUUID
                            .getOrDefault(environmentId, Collections.emptyList());

                        Set<String> contentOfLowerEnvs = contentOf(mapOfEnvironmentContentUUID, currentLower);

                        entToRegen = contentProvided.stream()
                            .filter(entitlementContentUUIDs::contains)
                            .anyMatch(contentOfLowerEnvs::contains);

                        if (entToRegen) {
                            break;
                        }
                    }
                }

                if (entToRegen) {
                    entitlementsToRegen.add(entitlementId);
                }
            }
        }

        return entitlementsToRegen;
    }

    private Set<String> contentOf(Map<String, List<String>> envContents, List<String> envIds) {
        return envIds.stream()
            .map(envContents::get)
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    }

    private List<String> findCurrentLower(List<String> envRemoved, List<String> preExistingEnvs,
        String environmentId) {
        int fromIndex = envRemoved.indexOf(environmentId) + 1;
        List<String> currentLower = new ArrayList<>(
            preExistingEnvs.subList(fromIndex, preExistingEnvs.size()));
        return (List<String>) differenceOf(envRemoved, currentLower);
    }

    private List<String> findCurrentHigher(List<String> updatedEnvs, String environmentId) {
        if (updatedEnvs.contains(environmentId)) {
            return updatedEnvs.subList(0, updatedEnvs.indexOf(environmentId));
        }
        else {
            return updatedEnvs;
        }
    }

    /**
     * Returns the list of environment IDs which got added, removed or re-prioritized
     * from consumers current existing environments.
     *
     * @return
     *  Returns the unique environmentIds which got added, removed or re-prioritized
     */
    private Set<String> getEnvironmentChangeList(List<String> envRemoved,
        List<String> preExistingEnvs, List<String> updatedEnvs) {

        Set<String> environmentChangeList = new HashSet<>();

        // Envs Added
        environmentChangeList.addAll(differenceOf(preExistingEnvs, updatedEnvs));

        // Env removed are kept in separate list for special use cases
        environmentChangeList.addAll(envRemoved);

        // Env reordered
        environmentChangeList.addAll(Util.getReorderedItems(preExistingEnvs, updatedEnvs));

        return environmentChangeList;
    }

    @SuppressWarnings("unchecked")
    private <T> Collection<T> differenceOf(List<T> preExistingEnvs, List<T> updatedEnvs) {
        return CollectionUtils.subtract(updatedEnvs, preExistingEnvs);
    }

    /**
     * Method to check if entitlement cert needs to be regenerated or not,
     * based on environment content UUIDs, content UUIDs of environment having
     * higher priority & content UUIDs provided by entitlement itself.
     *
     * @param environmentContentUUIDs
     *  Content UUIDs associated with environment
     *
     * @param entitlementContentUUIDs
     *  Content UUIDs associated with entitlement
     *
     * @param currentHigherContentUUIDs
     *  Content UUIDs belonging to environment having higher priority
     *
     * @return
     *  Returns true or false, whether to regenerate entitlement cert or not.
     */

    private boolean isCertRegenerationRequired(List<String> environmentContentUUIDs,
        Set<String> entitlementContentUUIDs, Set<String> currentHigherContentUUIDs) {
        boolean regenRequired = false;
        // If current env is of the highest priority,
        // we only check if any one of environment content UUID is being provided
        // by the entitlement
        if (currentHigherContentUUIDs.isEmpty()) {
            for (String contentUUID : environmentContentUUIDs) {
                if (entitlementContentUUIDs.contains(contentUUID)) {
                    regenRequired = true;
                    break;
                }
            }
        }
        else {
            for (String contentUUID : environmentContentUUIDs) {
                if (entitlementContentUUIDs.contains(contentUUID) &&
                    !currentHigherContentUUIDs.contains(contentUUID)) {
                    regenRequired = true;
                    break;
                }
            }
        }

        return regenRequired;
    }
}
