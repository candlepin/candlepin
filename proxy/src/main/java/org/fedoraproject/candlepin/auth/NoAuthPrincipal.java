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
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Permission;
import org.fedoraproject.candlepin.model.Pool;

/**
 *
 */
public class NoAuthPrincipal extends Principal {

    public NoAuthPrincipal() {
        super(Arrays.asList(new Permission[] {new Permission(null, null)}));
    }

    @Override
    public String getType() {
        return Principal.NO_AUTH_TYPE;
    }

    @Override
    public String getPrincipalName() {       
        return "Anonymous";
    }

    @Override
    public boolean canAccess(Owner owner) {
        return false;
    }

    @Override
    public boolean canAccess(Consumer consumer) {
        return false;
    }

    @Override
    public boolean canAccess(Entitlement entitlement) {
        return false;
    }

    @Override
    public boolean canAccess(EntitlementCertificate entitlementCert) {
        return false;
    }

    @Override
    public boolean canAccess(Pool pool) {
        return false;
    }
}
