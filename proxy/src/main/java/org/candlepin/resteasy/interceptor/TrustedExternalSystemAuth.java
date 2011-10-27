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
package org.candlepin.resteasy.interceptor;

import org.candlepin.auth.ExternalSystemPrincipal;
import org.candlepin.auth.Principal;
import org.jboss.resteasy.spi.HttpRequest;

/**
 * TrustedExternalSystemAuth
 *
 * This auth form allows and external system to authenticate via
 * the shared secret, and act as itself.
 *
 * This is different from the User or Consumer auth versions, in that the
 * External System will not be acting on behalf of another
 * (and has different permission levels).
 */
public class TrustedExternalSystemAuth implements AuthProvider {

    @Override
    public Principal getPrincipal(HttpRequest request) {
        // TODO Auto-generated method stub
        return new ExternalSystemPrincipal();
    }

}
