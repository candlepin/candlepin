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
package org.candlepin.guice;

import org.candlepin.auth.Principal;

/**
 * TestPrincipalProviderSetter
 */
public class TestPrincipalProviderSetter {
    private static TestPrincipalProviderSetter instance;
    private Principal principal;

    public static TestPrincipalProviderSetter get() {
        if (instance == null) {
            instance = new TestPrincipalProviderSetter();
        }
        return instance;
    }

    public Principal getPrincipal() {
        return principal;
    }

    public void setPrincipal(Principal setMe) {
        this.principal = setMe;
    }
}
