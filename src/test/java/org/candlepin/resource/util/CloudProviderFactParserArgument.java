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

package org.candlepin.resource.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

record CloudProviderFactParserArgument(Map<String, String> facts, boolean supported, String accountId, String instanceId, List<String> offeringIds) {

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {
        private Map<String, String> facts;
        private String accountId;
        private String instanceId;
        private List<String> offeringIds;
        private boolean supported;

        Builder asSupported() {
            this.supported = true;
            return this;
        }

        Builder asNotSupported() {
            this.supported = false;
            return this;
        }

        Builder withAccountId(String accountId) {
            this.accountId = accountId;
            return this;
        }

        Builder withInstanceId(String instanceId) {
            this.instanceId = instanceId;
            return this;
        }

        Builder withOfferingId(String offeringId) {
            if (offeringIds == null)
                offeringIds = new ArrayList<>();
            offeringIds.add(offeringId);
            return this;
        }

        Builder withFact(String key, String value) {
            if( facts == null)
                facts = new HashMap<>();
            facts.put(key, value);
            return this;
        }

        Builder withEmptyFacts() {
            this.facts = new HashMap<>();
            return this;
        }

        CloudProviderFactParserArgument build() {
            return new CloudProviderFactParserArgument(facts, supported, accountId, instanceId, offeringIds);
        }

    }
}
