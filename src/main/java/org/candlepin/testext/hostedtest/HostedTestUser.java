package org.candlepin.testext.hostedtest;

import java.util.Collection;
import java.util.Date;

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

    public HostedTestUser setIsSuperAdmin(boolean isSuperAdmin) {
        this.isSuperAdmin = isSuperAdmin;
        return this;
    }

    @Override
    public Boolean isSuperAdmin() {
        return isSuperAdmin;
    }

    public void setPrimaryOwner(OwnerInfo owner) {
        this.owner = owner;
    }

    @Override
    public OwnerInfo getPrimaryOwner() {
        return owner;
    }

    public void setRoles(Collection<HostedTestRole> roles) {
        this.roles = roles;
    }

    @Override
    public Collection<? extends RoleInfo> getRoles() {
        return roles;
    }

}
