/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.guice;

import org.candlepin.auth.Principal;

import com.google.inject.Provider;

import org.jboss.resteasy.core.ResteasyContext;

import javax.inject.Singleton;

/**
 * Guice provider that pulls the principal out of the ResteasyProviderFactory's context.
 * This class is not servlet-scoped because Pinsetter also uses it.
 */
@Singleton
public class PrincipalProvider implements Provider<Principal> {
    @Override
    public Principal get() {
        return ResteasyContext.getContextData(Principal.class);
    }
}
