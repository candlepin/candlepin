/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.resource;

import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.EntitlementPoolCurator;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;

import com.google.inject.Inject;

import org.apache.log4j.Logger;

import java.util.LinkedList;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * Owner Resource
 */
@Path("/owner")
public class OwnerResource {
    
    private static Logger log = Logger.getLogger(OwnerResource.class);
    private OwnerCurator ownerCurator;
    private EntitlementPoolCurator entitlementPoolCurator;

    /**
     * @param modelClassIn
     */
    @Inject
    public OwnerResource(OwnerCurator ownerCurator, EntitlementPoolCurator entitlementPoolCurator) {
        this.ownerCurator = ownerCurator;
        this.entitlementPoolCurator = entitlementPoolCurator;
    }

    /**
     * Return list of Owners
     * @return list of Owners
     */
    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    public List<Owner> list() {
        return ownerCurator.findAll();  
    }

    @GET
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    @Path("{owner_id}/entitlement")
    public List<Entitlement> ownerEntitlements(@PathParam("owner_id") Long ownerId) {
        Owner owner = ownerCurator.find(ownerId);
        if (owner == null) {
            throw new NotFoundException("owner with id: " + ownerId + " was not found.");
        }
        
        List<Entitlement> toReturn = new LinkedList<Entitlement>();
        for (EntitlementPool pool: owner.getEntitlementPools()) {
            toReturn.addAll(entitlementPoolCurator.entitlementsIn(pool));
        }
        
        return toReturn;
    }
}
