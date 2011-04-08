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
package org.fedoraproject.candlepin.resteasy.interceptor;

import java.util.List;

import org.fedoraproject.candlepin.exceptions.NotFoundException;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.jboss.resteasy.spi.HttpRequest;

/**
 * AuthUtil
 */
public class AuthUtil {
    
    private AuthUtil() {        
    }
    
    /**
     * Retrieve a header, or the empty string if it is not there.
     * 
     * @return the header or a blank string (no nils)
     */
    public static String getHeader(HttpRequest request, String name) {
        String headerValue = "";
        List<String> header = null;
        System.out.println(request.getHttpHeaders());
        System.out.println(request.getHttpHeaders().getRequestHeaders());
        for (String key : request.getHttpHeaders().getRequestHeaders().keySet()) {
            if (key.equalsIgnoreCase(name)) {
                header = request.getHttpHeaders().getRequestHeader(key);
                break;
            }
        }
        if (null != header && header.size() > 0) {
            headerValue = header.get(0);
        }
        return headerValue;
    }
    
    /**
     * Look up an owner. Throw an NotFoundException if not found.
     * @param owner object not retrieved from the DB.
     * @param ownerCurator locaion of object in the DB
     * @return the owner from the DB
     */
    public static Owner lookupOwner(Owner owner, OwnerCurator ownerCurator) {
        Owner o = ownerCurator.lookupByKey(owner.getKey());
        if (o == null) {
            if (owner.getKey() == null) {
                throw new NotFoundException(
                    "An owner does not exist for a null org id");
            }

            o = owner;
        }

        return o;
    }    
}
