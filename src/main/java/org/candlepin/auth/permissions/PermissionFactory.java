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

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Singleton;



/**
 * PermissionFactory: Creates concrete Java permission classes based on the provided permission info
 */
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
        OWNER_HYPERVISORS,
        MANAGE_ACTIVATION_KEYS
    }

    /**
     * Performs simple resolution of owner instances needed to build permissions.
     *
     * @deprecated
     *  This is a temporary class used purely to perform fast resolution in lieu of a proper
     *  resolution utility. It should not be carried forward any further than absolutely
     *  necessary, and should be replaced by a proper resolver as soon as possible.
     */
    @Deprecated
    protected static class OwnerResolver {
        private static final WeakReference<Owner> EMPTY_REFERENCE = new WeakReference<>(null);

        private final OwnerCurator ownerCurator;
        private final WeakHashMap<String, WeakReference<Owner>> ownerCache;

        public OwnerResolver(OwnerCurator ownerCurator) {
            this.ownerCurator = Objects.requireNonNull(ownerCurator);
            this.ownerCache = new WeakHashMap<>();
        }

        public Owner resolve(OwnerInfo oinfo) {
            if (oinfo == null) {
                return null;
            }

            String ownerKey = oinfo.getKey();
            Owner cached = this.ownerCache.getOrDefault(ownerKey, EMPTY_REFERENCE)
                .get();

            // If the owner has either never been resolved or was booted out of memory, perform the
            // lookup and store the result (again).
            if (cached == null) {
                cached = this.ownerCurator.getByKey(ownerKey);
                if (cached == null) {
                    return null;
                }

                // Impl note:
                // We're abusing the fact that we expect the owner to hold a reference to its own
                // key, so the ref count on `cached.getKey()` should be at least 1 until after the
                // weak ref value is cleared as a result of the owner itself falling out of scope.
                this.ownerCache.put(cached.getKey(), new WeakReference<>(cached));
            }

            return cached;
        }
    }

    /**
     * Micro-interface used to create simple permission builders from a user and a permission
     * blueprint.
     */
    @FunctionalInterface
    public interface PermissionBuilder {
        List<Permission> build(UserInfo user, PermissionBlueprintInfo blueprint, OwnerResolver resolver);
    }

    private final OwnerCurator ownerCurator;
    private final OwnerResolver ownerResolver;

    private final Map<String, PermissionBuilder> builders;

    @Inject
    public PermissionFactory(OwnerCurator ownerCurator) {
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.ownerResolver = new OwnerResolver(this.ownerCurator);

        this.builders = this.initBuilders();
    }

    /**
     * Initializes the permission builders used by this factory.
     */
    @SuppressWarnings("checkstyle:indentation")
    private Map<String, PermissionBuilder> initBuilders() {
        Map<String, PermissionBuilder> builders = new HashMap<>();
        builders.put(PermissionType.OWNER.name(), (user, bp, res) -> {
            Owner owner = res.resolve(bp.getOwner());
            return owner != null ?
                List.of(new OwnerPermission(owner, Access.valueOf(bp.getAccessLevel()))) :
                List.of();
        });

        builders.put(PermissionType.OWNER_POOLS.name(), (user, bp, res) -> {
            Owner owner = res.resolve(bp.getOwner());
            return owner != null ?
                List.of(new OwnerPoolsPermission(owner)) :
                List.of();
        });

        builders.put(PermissionType.USERNAME_CONSUMERS.name(), (user, bp, res) -> {
            Owner owner = res.resolve(bp.getOwner());
            return owner != null ?
                List.of(new UsernameConsumersPermission(user, res.resolve(bp.getOwner()))) :
                List.of();
        });

        // At the time of writing, no matching permission exists for USERNAME_CONSUMERS_ENTITLEMENTS

        builders.put(PermissionType.ATTACH.name(), (user, bp, res) -> {
            Owner owner = res.resolve(bp.getOwner());
            return owner != null ?
                List.of(new AttachPermission(owner)) :
                List.of();
        });

        builders.put(PermissionType.OWNER_HYPERVISORS.name(), (user, bp, res) -> {
            Owner owner = res.resolve(bp.getOwner());
            return owner != null ?
                List.of(new ConsumerOrgHypervisorPermission(owner)) :
                List.of();
        });

        builders.put(PermissionType.MANAGE_ACTIVATION_KEYS.name(), (user, bp, res) -> {
            Owner resolved = res.resolve(bp.getOwner());
            if (resolved == null) {
                return List.of();
            }

            Access accessLevel = Access.valueOf(bp.getAccessLevel());

            // Impl note:
            // Activation key verifications are split between two types:
            //  - @Verify(Owner.class, subResource=SubResources.ACTIVATION_KEY)
            //  - @Verify(ActivationKey.class)
            //
            // As a result of this, we need to return both permission objects with the appropriate
            // level to ensure all of the CRUD operations are covered
            return List.of(
                new ActivationKeyPermission(resolved, accessLevel),
                new OwnerActivationKeyPermission(resolved, accessLevel));
        });

        return builders;
    }

    /**
     * Performs the work of permission creation for the given user and permission blueprint. Should
     * only be called after the inputs have been validated elsewhere.
     *
     * @param user
     *  The user for which to create a permission
     *
     * @param blueprint
     *  The permission blueprint to use to create the user permissions
     *
     * @return
     *  A collection of concrete permissions created from the given permission blueprint for the provided user
     */
    public List<Permission> createPermissions(UserInfo user, PermissionBlueprintInfo blueprint) {
        if (user == null) {
            throw new IllegalArgumentException("user is null");
        }

        if (blueprint == null) {
            // nothing to build
            return List.of();
        }

        PermissionBuilder builder = this.builders.get(blueprint.getTypeName());
        if (builder == null) {
            log.warn("Unsupported permission type: {}", blueprint.getTypeName());
            return List.of();
        }

        return builder.build(user, blueprint, this.ownerResolver);
    }

    /**
     * Converts the provided permission info into concrete permissions for the given user. If the
     * provided permission blueprints do not result in any explicit permissions for the given user,
     * this method returns an empty list.
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
    public List<Permission> createPermissions(UserInfo user,
        Collection<? extends PermissionBlueprintInfo> blueprints) {

        if (user == null) {
            throw new IllegalArgumentException("user is null");
        }

        return (blueprints != null ? blueprints.stream() : Stream.<PermissionBlueprintInfo>empty())
            .filter(Objects::nonNull)
            .map(bp -> this.createPermissions(user, bp))
            .flatMap(Collection::stream)
            .toList();
    }

    /**
     * Builds a collection of permissions applicable to the user based on their roles and any
     * explicitly granted access rights. If the user is not granted any special permissions, this
     * method returns an empty collection.
     *
     * @param user
     *  The user info of the user for which to build permissions
     *
     * @throws IllegalArgumentException
     *  if user is null
     *
     * @return
     *  a collection of permissions applicable to the provided user
     */
    public List<Permission> createPermissions(UserInfo user) {
        if (user == null) {
            throw new IllegalArgumentException("user is null");
        }

        Collection<? extends RoleInfo> roles = user.getRoles();

        return (roles != null ? roles.stream() : Stream.<RoleInfo>empty())
            .filter(Objects::nonNull)
            .map(role -> this.createPermissions(user, role.getPermissions()))
            .flatMap(Collection::stream)
            .toList();
    }
}
