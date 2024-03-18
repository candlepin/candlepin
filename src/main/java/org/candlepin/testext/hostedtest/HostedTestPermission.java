package org.candlepin.testext.hostedtest;

import org.candlepin.service.model.OwnerInfo;
import org.candlepin.service.model.PermissionBlueprintInfo;

public class HostedTestPermission implements PermissionBlueprintInfo {

    private String id;
    private OwnerInfo owner;
    private String typeName;
    private String accessLevel;

    public HostedTestPermission setId(String id) {
        this.id = id;
        return this;
    }

    public String getId() {
        return id;
    }

    public HostedTestPermission setOwner(OwnerInfo owner) {
        this.owner = owner;
        return this;
    }

    @Override
    public OwnerInfo getOwner() {
        return owner;
    }

    public HostedTestPermission setTypeName(String typeName) {
        this.typeName = typeName;
        return this;
    }

    @Override
    public String getTypeName() {
        return typeName;
    }

    public HostedTestPermission setAccessLevel(String accessLevel) {
        this.accessLevel = accessLevel;
        return this;
    }

    @Override
    public String getAccessLevel() {
        return accessLevel;
    }

}

