/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;



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
     * Fetches a set of entitlement IDs whose certificates should be regenerated as a result of
     * the given environment updates. If no entitlements need regeneration, this method returns
     * an empty set.
     *
     * @param updates
     *  an EnvironmentUpdates instance containing the environment changes to process
     *
     * @throws IllegalArgumentException
     *  if updates is null
     *
     * @return
     *  a set of entitlement IDs whose certificates need regeneration as a result of the given
     *  environment updates
     */
    public Set<String> filterEntitlements(EnvironmentUpdates updates) {
        if (updates == null) {
            throw new IllegalArgumentException("updates is null");
        }

        Set<String> entitlementsToRegen = new HashSet<>();

        Map<String, Set<String>> environmentContentIdMap = this.environmentContentCurator
            .getEnvironmentContentIdMap(updates.environments());

        Map<String, String> entConsumerIdMap = this.entitlementCurator
            .getEntitlementConsumerIdMap(updates.consumers());

        Map<String, Set<String>> entitlementContentIdMap = this.entitlementCurator
            .getEntitlementContentIdMap(entConsumerIdMap.keySet());

        for (Map.Entry<String, Set<String>> entry : entitlementContentIdMap.entrySet()) {
            String entitlementId = entry.getKey();
            Set<String> entContentIds = entry.getValue();

            String consumerId = entConsumerIdMap.get(entitlementId);
            List<String> currentEnvList = updates.currentEnvsOf(consumerId);
            List<String> updatedEnvList = updates.updatedEnvsOf(consumerId);

            // Entitlement needs to be regenerated if any of the following occur:
            // - the first environment that has promoted the content has changed
            // - all environments that have promoted the content are removed
            // - the content was not promoted previously

            for (String contentId : entContentIds) {
                String cEnv = this.getContentEnvironment(contentId, currentEnvList, environmentContentIdMap);
                String uEnv = this.getContentEnvironment(contentId, updatedEnvList, environmentContentIdMap);

                if (uEnv != null ? !uEnv.equals(cEnv) : cEnv != null) {
                    entitlementsToRegen.add(entitlementId);
                    break;
                }
            }
        }

        return entitlementsToRegen;
    }

    /**
     * Fetches the first environment in the given list in which the specified content is promoted.
     * If the content has not been promoted in any of the environments, this method returns null.
     *
     * @param contentId
     *  the ID of the content to lookup
     *
     * @param environmentIds
     *  the list of environments to check, in descending order of priority
     *
     * @map environmentContentIdMap
     *  a map consisting of the environment IDs mapped to their promoted content IDs
     *
     * @return
     *  the ID of the first environment in which the specified content is promoted, or null if the
     *  content has not been promoted in any of the given environments
     */
    private String getContentEnvironment(String contentId, List<String> environmentIds,
        Map<String, Set<String>> environmentContentIdMap) {

        for (String envId : environmentIds) {
            if (environmentContentIdMap.getOrDefault(envId, Collections.emptySet()).contains(contentId)) {
                return envId;
            }
        }

        return null;
    }

}
