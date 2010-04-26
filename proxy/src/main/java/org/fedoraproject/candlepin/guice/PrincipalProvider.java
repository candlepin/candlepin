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
package org.fedoraproject.candlepin.guice;

import com.google.inject.Inject;
import com.google.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import org.fedoraproject.candlepin.auth.Principal;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

/**
 *
 */
public class PrincipalProvider implements Provider<Principal> {

    private Principal principal;

    @Inject
    public PrincipalProvider(@Context HttpServletRequest request) {
        principal = ResteasyProviderFactory.getContextData(Principal.class);
    }

    @Override
    public Principal get() {
        return principal;
    }

}
