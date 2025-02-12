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

import static org.candlepin.model.CloudIdentifierFacts.GCP_INSTANCE_ID;
import static org.candlepin.model.CloudIdentifierFacts.GCP_LICENSE_CODES;
import static org.candlepin.model.CloudIdentifierFacts.GCP_PROJECT_ID;
import static org.candlepin.model.CloudIdentifierFacts.GCP_SHORT_NAME;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GCPProviderFactParser implements CloudProviderFactParser {

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> getAccountId(Map<String, String> facts) {
        if (facts == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(facts.get(GCP_PROJECT_ID.getValue()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<List<String>> getOfferingIds(Map<String, String> facts) {
        if (facts == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(facts.get(GCP_LICENSE_CODES.getValue())).map(List::of);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<String> getInstanceId(Map<String, String> facts) {
        if (facts == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(facts.get(GCP_INSTANCE_ID.getValue()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getShortName() {
        return GCP_SHORT_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSupported(Map<String, String> facts) {
        if (facts == null) {
            return false;
        }

        return facts.containsKey(GCP_PROJECT_ID.getValue()) ||
            facts.containsKey(GCP_INSTANCE_ID.getValue()) ||
            facts.containsKey(GCP_LICENSE_CODES.getValue());
    }
}
