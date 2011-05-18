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
package org.fedoraproject.candlepin.auth;

import java.util.Arrays;
import org.fedoraproject.candlepin.model.Permission;

/**
 * ExternalSystemPrincipal - A principal representing a trusted external system
 * (ie another candlepin instance, a proxy)
 */
public class ExternalSystemPrincipal extends Principal {

    /**
     * @param owner
     * @param roles
     */
    public ExternalSystemPrincipal() {
        super(Arrays.asList(new Permission[] 
            {new Permission(null, Role.TRUSTED_SYSTEM)}));
    }

    @Override
    public String getType() {
        return "system";
    }

    @Override
    public String getPrincipalName() {       
        return "External System";
    }     

}
