package org.candlepin.testext.hostedtest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import org.candlepin.service.model.PermissionBlueprintInfo;
import org.candlepin.service.model.RoleInfo;
import org.candlepin.service.model.UserInfo;

public class HostedTestRole implements RoleInfo {

    private Date created;
    private Date updated;
    private String name;
    private Collection<HostedTestUser> users = new ArrayList<>();
    private Collection<HostedTestPermission> permissions = new ArrayList<>();

    public HostedTestRole setCreated(Date created) {
        this.created = created;
        return this;
    }

    @Override
    public Date getCreated() {
        return created;
    }

    public HostedTestRole setUpdated(Date updated) {
        this.updated = updated;
        return this;
    }

    @Override
    public Date getUpdated() {
        return updated;
    }

    public HostedTestRole setName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public String getName() {
        return name;
    }

    public HostedTestRole addUser(HostedTestUser user) {
        this.users.add(user);
        return this;
    }

    public HostedTestRole removeUser(String username) {
        if (username == null) {
            return this;
        }

        this.users.removeIf(user -> username.equals(user.getUsername()));
        return this;
    }

    public HostedTestRole setUsers(Collection<HostedTestUser> users) {
        this.users = users;
        return this;
    }

    public boolean hasUser(String username) {
        if (username == null) {
            return false;
        }

        // TODO: Can we improve this?
        for (HostedTestUser user : users) {
            if (username.equals(user.getUsername())) {
                return true;
            }
        }

        return false;
    }

    @Override
    public Collection<? extends UserInfo> getUsers() {
        return users;
    }

    public HostedTestRole addPermission(HostedTestPermission permission) {
        this.permissions.add(permission);
        return this;
    }

    public HostedTestRole removePermission(String permissionId) {
        if (permissions == null) {
            return this;
        }

        this.permissions.removeIf(permission -> permissionId.equals(permission.getId()));
        return this;
    }

    public HostedTestRole setPermissions(Collection<HostedTestPermission> permissions) {
        this.permissions = permissions;
        return this;
    }

    @Override
    public Collection<? extends PermissionBlueprintInfo> getPermissions() {
        return permissions;
    }

}
