package org.candlepin.testext.hostedtest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.candlepin.service.model.OwnerInfo;
import org.candlepin.service.model.RoleInfo;
import org.candlepin.service.model.UserInfo;

public class HostedTestUser implements UserInfo {

    private Date created;
    private Date updated;
    private String username;
    private String password;
    private Boolean isSuperAdmin;
    private OwnerInfo owner;
    private Collection<HostedTestRole> roles;

    public HostedTestUser() {
        // Intentionally left blank
    }

    public HostedTestUser(UserInfo userInfo) {
        this.created = userInfo.getCreated();
        this.updated = userInfo.getUpdated();
        this.username = userInfo.getUsername();
        this.password = userInfo.getHashedPassword();
        this.isSuperAdmin = userInfo.isSuperAdmin();
        this.owner = userInfo.getPrimaryOwner();
        this.roles = HostedTestRole.fromRoleInfo(userInfo.getRoles());
    }

    public HostedTestUser setCreated(Date created) {
        this.created = created;
        return this;
    }

    @Override
    public Date getCreated() {
        return created;
    }

    public HostedTestUser setUpdated(Date updated) {
        this.updated = updated;
        return this;
    }

    @Override
    public Date getUpdated() {
        return updated;
    }

    public HostedTestUser setUsername(String username) {
        this.username = username;
        return this;
    }

    @Override
    public String getUsername() {
        return username;
    }

    public HostedTestUser setHashedPassword(String password) {
        this.password = password;
        return this;
    }

    @Override
    public String getHashedPassword() {
        return password;
    }

    public HostedTestUser setSuperAdmin(boolean isSuperAdmin) {
        this.isSuperAdmin = isSuperAdmin;
        return this;
    }

    @Override
    public Boolean isSuperAdmin() {
        return isSuperAdmin;
    }

    public HostedTestUser setPrimaryOwner(OwnerInfo owner) {
        this.owner = owner;
        return this;
    }

    @Override
    public OwnerInfo getPrimaryOwner() {
        return owner;
    }

    public HostedTestUser addRole(HostedTestRole role) {
        this.roles.add(role);
        return this;
    }

    public HostedTestUser removeRole(String roleName) {
        if (roleName == null) {
            return null;
        }

        this.roles.removeIf(existingRole -> roleName.equals(existingRole.getName()));
        return this;
    }

    public boolean hasRole(String roleName) {
        if (roleName == null) {
            return false;
        }

        // TODO: Can we improve this?
        for (HostedTestRole role : roles) {
            if (roleName.equals(role.getName())) {
                return true;
            }
        }

        return false;
    }

    public void setRoles(Collection<HostedTestRole> roles) {
        this.roles = roles;
    }

    @Override
    public Collection<? extends RoleInfo> getRoles() {
        return roles;
    }

    public static List<HostedTestUser> fromUserInfo(Collection<? extends UserInfo> userInfo) {
        List<HostedTestUser> convertedUsers = new ArrayList<>();

        //TODO: Can we improve this?
        for (UserInfo user : userInfo) {
            convertedUsers.add(new HostedTestUser(user));
        }

        return convertedUsers;
    }

}
