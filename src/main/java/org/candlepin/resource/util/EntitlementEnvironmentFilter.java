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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The EntitlementEnvironmentFilter class filters the consumer's entitlement
 * whose entitlement certificates are needed to be re-generated because of changes
 * (addition, removal or re-prioritization) in consumer existing environment.
 * This filtering avoids re-generating all the consumer entitlement certificates.
 */
public class EntitlementEnvironmentFilter {
    private EntitlementCurator entitlementCurator;
    private EnvironmentContentCurator environmentContentCurator;
    private List<String> preExistingEnvs;
    private List<String> updatedEnvs;
    private List<String> consumerIds;
    private List<String> envRemoved;


    public EntitlementEnvironmentFilter(EntitlementCurator entitlementCurator,
        EnvironmentContentCurator environmentContentCurator) {
        this.entitlementCurator = entitlementCurator;
        this.environmentContentCurator = environmentContentCurator;
        this.envRemoved = new ArrayList<>();
    }

    public EntitlementEnvironmentFilter setPreExistingEnvironments(List<String> preExistingEnvs) {
        this.preExistingEnvs = preExistingEnvs;
        return this;
    }

    public EntitlementEnvironmentFilter setUpdatedEnvironment(List<String> updatedEnvs) {
        this.updatedEnvs = updatedEnvs;
        return this;
    }

    public EntitlementEnvironmentFilter setConsumerToBeUpdated(List<String> consumerIds) {
        this.consumerIds = consumerIds;
        return this;
    }

    /**
     * Returns the list of environment IDs which got added, removed or re-prioritized
     * from consumers current existing environments.
     *
     * @return
     *  Returns the unique environmentIds which got added, removed or re-prioritized
     */
    private Set<String> getEnvironmentChangeList() {
        Set<String> environmentChangeList = new HashSet<>();

        // Envs Added
        environmentChangeList.addAll(CollectionUtils.subtract(this.updatedEnvs, this.preExistingEnvs));

        // Env removed are kept in separate list for special use cases
        envRemoved.addAll(CollectionUtils.subtract(this.preExistingEnvs, this.updatedEnvs));
        environmentChangeList.addAll(CollectionUtils.subtract(this.preExistingEnvs, this.updatedEnvs));

        // Env reordered
        environmentChangeList.addAll(Util.getReorderedItems(this.preExistingEnvs, this.updatedEnvs));

        return environmentChangeList;
    }

    /**
     * Filters the entitlements whose certificates are needed to be re-generated due to environments
     * being added, removed or re-prioritized for a consumer.
     * Idea is to avoid regenerating all the consumer's entitlements certs & filter the
     * necessary entitlements whose certificates are actually needed to be re-generated.
     *
     * @return
     *  List of entitlement IDs selected for certificate regeneration
     */
    public Set<String> filterEntitlements() {
        Set<String> entitlementsToRegen = new HashSet<>();
        Set<String> environmentChangeList = getEnvironmentChangeList();
        // get contentUUIDs for all envs
        Set<String> allEnvIds = new HashSet<>();
        allEnvIds.addAll(environmentChangeList);
        allEnvIds.addAll(updatedEnvs);

        Map<String, List<String>> mapOfEnvironmentContentUUID = this.environmentContentCurator
            .getEnvironmentContentUUIDs(allEnvIds);
        Map<String, Set<String>> mapOfEntitlementContentUUID = this.entitlementCurator
            .getEntitlementContentUUIDs(consumerIds);

        for (String entitlementId : mapOfEntitlementContentUUID.keySet()) {
            boolean entToRegen = false;
            Set<String> entitlementContentUUIDs = mapOfEntitlementContentUUID.get(entitlementId);

            for (String environmentId : environmentChangeList) {
                List<String> currentHigher = new ArrayList<>();
                Set<String> currentHigherContentUUIDs = new HashSet<>();
                List<String> environmentContentUUIDs = mapOfEnvironmentContentUUID.get(environmentId);

                if (environmentContentUUIDs == null) {
                    // No content present for this env
                    continue;
                }

                if (this.updatedEnvs.contains(environmentId)) {
                    currentHigher.addAll(this.updatedEnvs
                        .subList(0, this.updatedEnvs.indexOf(environmentId)));
                }
                else {
                    currentHigher.addAll(this.updatedEnvs);
                }

                for (String higherEnv : currentHigher) {
                    if (mapOfEnvironmentContentUUID.get(higherEnv) != null) {
                        currentHigherContentUUIDs
                            .addAll(mapOfEnvironmentContentUUID.get(higherEnv));
                    }
                }

                if (isCertRegenerationRequired(environmentContentUUIDs,
                    entitlementContentUUIDs, currentHigherContentUUIDs)) {
                    entToRegen = true;
                    break;
                }
            }

            // Case where env of higher priority is being removed
            if (!envRemoved.isEmpty() && !entToRegen) {
                for (String environmentId : envRemoved) {
                    List<String> currentLower = new ArrayList<>();
                    currentLower.addAll(preExistingEnvs
                        .subList(envRemoved.indexOf(environmentId) + 1, preExistingEnvs.size()));
                    currentLower = (List<String>) CollectionUtils.subtract(currentLower, envRemoved);
                    List<String> contentProvided = mapOfEnvironmentContentUUID.get(environmentId);
                    Set<String> contentProvideByLowerEnvs = new HashSet<>();

                    for (String env : currentLower) {
                        contentProvideByLowerEnvs.addAll(mapOfEnvironmentContentUUID.get(env));
                    }

                    for (String content : contentProvided) {
                        if (entitlementContentUUIDs.contains(content) &&
                            contentProvideByLowerEnvs.contains(content)) {
                            entToRegen = true;
                            break;
                        }
                    }

                    if (entToRegen) {
                        break;
                    }
                }
            }

            if (entToRegen) {
                entitlementsToRegen.add(entitlementId);
            }
        }

        return entitlementsToRegen;
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
