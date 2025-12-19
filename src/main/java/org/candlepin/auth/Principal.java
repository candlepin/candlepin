/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.auth;

import org.candlepin.auth.permissions.Permission;
import org.candlepin.util.Util;

import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JacksonException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;


/**
 * An entity interacting with Candlepin
 */
public abstract class Principal implements Serializable, java.security.Principal {
    private static final long serialVersionUID = 907789978604269132L;

    private static Logger log = LoggerFactory.getLogger(Principal.class);
    protected List<Permission> permissions = new ArrayList<>();

    public abstract String getType();

    public abstract AuthenticationMethod getAuthenticationMethod();

    public abstract boolean hasFullAccess();

    public List<Permission> getPermissions() {
        return permissions;
    }

    protected void addPermission(Permission permission) {
        this.permissions.add(permission);
    }

    public boolean canAccess(Object target, SubResource subResource, Access access) {
        log.debug("{} principal checking for {} access to target: {} sub-resource: {}",
            this.getClass().getName(), access, target, subResource);

        if (hasFullAccess()) {
            return true;
        }

        for (Permission permission : permissions) {
            log.debug(" checking permission: {}", permission.getClass().getName());
            if (permission.canAccess(target, subResource, access)) {
                log.debug("  permission granted");
                // if any of the principal's permissions allows access, then
                // we are good to go
                return true;
            }
        }

        // none of the permissions grants access, so this target is not allowed
        String targetType = (target == null) ? "null" : target.getClass().getName();
        log.warn("Refused principal: '{}' access to: {}", getName(), targetType);
        return false;
    }

    public boolean canAccessAll(Collection targets, SubResource subResource, Access access) {
        if (CollectionUtils.isEmpty(targets)) {
            log.debug(
                "{} principal checking for {} access to sub-resource: {}." +
                " Access to null or resource tried",
                this.getClass().getName(), access, subResource);
            return canAccess(null, subResource, access);
        }

        log.debug("{} principal checking for {} access to targets: {} sub-resource: {}",
            this.getClass().getName(), access, Arrays.toString(targets.toArray()), subResource);

        if (hasFullAccess()) {
            return true;
        }

        for (Object target : targets) {
            if (!canAccess(target, subResource, access)) {
                return false;
            }
        }
        return true;
    }

    public abstract String getName();

    /**
     * @deprecated use getName() instead
     * @return Principal name
     */
    @Deprecated
    public String getPrincipalName() {
        return getName();
    }

    public PrincipalData getData() {
        return new PrincipalData(this.getType(), this.getName());
    }

    /**
     * @return Username for this principal, null if there is not one.
     */
    public String getUsername() {
        return null;
    }

    @Override
    public String toString() {
        try {
            return Util.toJson(this.getData());
        }
        catch (JacksonException e) {
            log.error("Error while building JSON for principal.", e);
            return "";
        }
    }
}
