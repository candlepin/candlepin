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
package org.candlepin.auth.permissions;

import org.candlepin.auth.Access;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.service.model.OwnerInfo;
import org.candlepin.service.model.PermissionBlueprintInfo;
import org.candlepin.service.model.RoleInfo;
import org.candlepin.service.model.UserInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;



/**
 * PermissionFactory: Creates concrete Java permission classes based on the provided permission info
 */
//@Component
@Singleton
public class PermissionFactory {
    private static Logger log = LoggerFactory.getLogger(PermissionFactory.class);

    /**
     * PermissionType: Key used to determine which class to create.
     */
    public enum PermissionType {
        OWNER,
        OWNER_POOLS,
        USERNAME_CONSUMERS,
        USERNAME_CONSUMERS_ENTITLEMENTS,
        ATTACH,
        OWNER_HYPERVISORS
    }

    /**
     * Performs simple resolution of various entities needed by the permissions. A new instance
     * should be created for each instance
     *
     * @deprecated
     *  This is a temporary class used purely to perform fast resolution in lieu of a proper
     *  resolution utility. It should not be carried forward any further than absolutely
     *  necessary, and should be replaced by a proper resolver as soon as possible.
     */
    @Deprecated
    protected static class Resolver {

        protected OwnerCurator ownerCurator;
        protected Map<String, Owner> ownerCache;

        public Resolver(OwnerCurator ownerCurator) {
            this.ownerCurator = ownerCurator;
            this.ownerCache = new HashMap<>();
        }

        public Owner resolve(OwnerInfo oinfo) {
            if (oinfo != null) {
                // If it's already an Owner instance, just cast it.
                if (oinfo instanceof Owner) {
                    return (Owner) oinfo;
                }

                // Nope. Guess we need to resolve it...
                Owner owner = this.ownerCache.get(oinfo.getKey());

                if (owner == null) {
                    owner = this.ownerCurator.getByKey(oinfo.getKey());

                    if (owner == null) {
                        throw new IllegalStateException("No such owner: " + oinfo.getKey());
                    }

                    ownerCache.put(owner.getKey(), owner);
                    return owner;
                }
            }

            return null;
        }
    }


    /**
     * Micro-interface used to create simple permission builders from a user and a permission
     * blueprint.
     */
    @FunctionalInterface
    public interface PermissionBuilder {
        Permission build(UserInfo user, PermissionBlueprintInfo blueprint, Resolver resolver);
    }

    protected Map<String, PermissionBuilder> builders;

    protected OwnerCurator ownerCurator;

    @Inject
    public PermissionFactory(OwnerCurator ownerCurator) {
        this.ownerCurator = ownerCurator;

        this.initBuilders();
    }

    /**
     * Initializes the permission builders used by this factory.
     */
    @SuppressWarnings("checkstyle:indentation")
    protected void initBuilders() {
        this.builders = new HashMap<>();

        this.builders.put(PermissionType.OWNER.name(),
            (user, bp, r) -> new OwnerPermission(r.resolve(bp.getOwner()),
                Access.valueOf(bp.getAccessLevel())));

        this.builders.put(PermissionType.OWNER_POOLS.name(),
            (user, bp, r) -> new OwnerPoolsPermission(r.resolve(bp.getOwner())));

        this.builders.put(PermissionType.USERNAME_CONSUMERS.name(),
            (user, bp, r) -> new UsernameConsumersPermission(user, r.resolve(bp.getOwner())));

        // At the time of writing, no matching permission exists for USERNAME_CONSUMERS_ENTITLEMENTS

        this.builders.put(PermissionType.ATTACH.name(),
            (user, bp, r) -> new AttachPermission(r.resolve(bp.getOwner())));

        this.builders.put(PermissionType.OWNER_HYPERVISORS.name(),
            (user, bp, r) -> new ConsumerOrgHypervisorPermission(r.resolve(bp.getOwner())));
    }

    /**
     * Converts the provided permission blueprint into a concrete permission for the given user.
     *
     * @param user
     *  The user info of the user for which to create a permission
     *
     * @param blueprint
     *  The permission blueprint to use to create the permission
     *
     * @throws IllegalArgumentException
     *  if user is null
     *
     * @return
     *  A concrete permission based on the provided user and permission blueprint
     */
    public Permission createPermission(UserInfo user, PermissionBlueprintInfo blueprint) {
        if (user == null) {
            throw new IllegalArgumentException("user is null");
        }

        return this.createPermissionImpl(user, blueprint, new Resolver(this.ownerCurator));
    }

    /**
     * Converts the provided permission info into concrete permissions for the given user. If the
     * provided permission blueprints do not result in any explicit permissions for the given user,
     * this method returns an empty collection.
     *
     * @param user
     *  The user info of the user for which to create a permission
     *
     * @param blueprints
     *  A collection of permission blueprints to use to create the concrete permissions
     *
     * @throws IllegalArgumentException
     *  if user is null
     *
     * @return
     *  A collection of concrete permissions based on the provided user and permission info
     */
    public Collection<Permission> createPermissions(UserInfo user,
        Collection<? extends PermissionBlueprintInfo> blueprints) {

        if (user == null) {
            throw new IllegalArgumentException("user is null");
        }

        Set<Permission> translated = new HashSet<>();

        if (blueprints != null) {
            Resolver resolver = new Resolver(this.ownerCurator);

            for (PermissionBlueprintInfo pbinfo : blueprints) {
                Permission permission = this.createPermissionImpl(user, pbinfo, resolver);

                if (permission != null) {
                    translated.add(permission);
                }
            }
        }

        return translated;
    }

    /**
     * Performs the work of permission creation for the given user and permission blueprint. Should
     * only be called after the inputs have been validated elsewhere.
     *
     * @param user
     *  The user info of the user for which to create a permission
     *
     * @param blueprint
     *  The permission blueprint to use to create the permission
     *
     * @param resolver
     *  The entity resolver to use during permission creation
     *
     * @return
     *  A concrete permission based on the provided user and permission blueprint
     */
    private Permission createPermissionImpl(UserInfo user, PermissionBlueprintInfo blueprint,
        Resolver resolver) {

        if (blueprint != null) {
            PermissionBuilder builder = this.builders.get(blueprint.getTypeName());
            if (builder != null) {
                return builder.build(user, blueprint, resolver);
            }

            log.warn("Unsupported permission type: {}", blueprint.getTypeName());
        }

        return null;
    }

    /**
     * Builds a collection of permissions applicable to the user based on their roles and any
     * explicitly granted access rights. If the user is not granted any special permissions, this
     * method returns an empty collection.
     *
     * @param user
     *  The user for which to build permissions
     *
     * @throws IllegalArgumentException
     *  if user is null
     *
     * @return
     *  a collection of permissions applicable to the provided user
     */
    public Collection<Permission> createUserPermissions(UserInfo user) {
        if (user == null) {
            throw new IllegalArgumentException("user is null");
        }

        Set<Permission> permissions = new HashSet<>();

        if (user.getRoles() != null) {
            for (RoleInfo role : user.getRoles()) {
                if (role != null) {
                    permissions.addAll(this.createPermissions(user, role.getPermissions()));
                }
            }
        }

        return permissions;
    }
}
