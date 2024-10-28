/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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
package org.candlepin.spec.bootstrap.client.api;

import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.invoker.client.ApiClient;
import org.candlepin.resource.client.v1.EntitlementsApi;

import java.util.ArrayList;
import java.util.List;

public class EntitlementClient extends EntitlementsApi {

    public EntitlementClient(ApiClient client) {
        super(client);
    }

    public List<EntitlementDTO> listAllForConsumer(String consumerUuid) {
        if (consumerUuid == null || consumerUuid.isBlank()) {
            return new ArrayList<>();
        }

        return super.listAllForConsumer(consumerUuid, null, null, null, null, null, null);
    }
}
