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

import org.fedoraproject.candlepin.model.ObjectFactory;
import org.fedoraproject.candlepin.model.Owner;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * Owner Resource
 */
@Path("/owner")
public class OwnerResource extends BaseResource {
    
    /**
     * @param modelClassIn
     */
    public OwnerResource() {
        super(Owner.class);
    }

    /**
     * Return list of Owners
     * @return list of Owners
     */
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<Owner> list() {
        List<Object> u = ObjectFactory.get().listObjectsByClass(getApiClass());
        List<Owner> owners = new ArrayList<Owner>();
        for (Object o : u) {
            owners.add((Owner) o);
        }
        return owners;
    }

}
