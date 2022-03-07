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

package org.candlepin.spec.bootstrap.client;

import org.candlepin.ApiClient;
import org.candlepin.ApiException;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.resource.OwnerApi;

import java.util.HashSet;
import java.util.Set;

public class OwnerClient extends OwnerApi implements SpecTestClient {

    private final Set<String> createdOwners = new HashSet<>();

    public OwnerClient(ApiClient client) {
        super(client);
    }

    @Override
    public OwnerDTO createOwner(OwnerDTO ownerDTO) throws ApiException {
        this.createdOwners.add(ownerDTO.getKey());
        return super.createOwner(ownerDTO);
    }

    @Override
    public void deleteOwner(String ownerKey, Boolean revoke, Boolean force) throws ApiException {
        this.createdOwners.remove(ownerKey);
        super.deleteOwner(ownerKey, revoke, force);
    }

    /**
     * This is separate from the individual deleteOwner because of the collection clearing
     */
    public void cleanup() {
        for (String ownerKey : this.createdOwners) {
            try {
                super.deleteOwner(ownerKey, true, true);
            }
            catch (ApiException ae) {
                // it was not created on the server during the test
            }
        }
        this.createdOwners.clear();
    }

}
