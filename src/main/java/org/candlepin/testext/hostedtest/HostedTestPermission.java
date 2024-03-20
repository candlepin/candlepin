package org.candlepin.testext.hostedtest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.candlepin.service.model.OwnerInfo;
import org.candlepin.service.model.PermissionBlueprintInfo;
import org.candlepin.util.Util;

public class HostedTestPermission implements PermissionBlueprintInfo {

    private String id;
    private OwnerInfo owner;
    private String typeName;
    private String accessLevel;

    public HostedTestPermission() {
        // Intentionally left blank
    }

    public HostedTestPermission(PermissionBlueprintInfo blueprintInfo) {
        this.id = Util.generateUUID();
        this.owner = blueprintInfo.getOwner();
        this.typeName = blueprintInfo.getTypeName();
        this.accessLevel = blueprintInfo.getAccessLevel();
    }

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

    public static List<HostedTestPermission> fromPermissionBlueprintInfo(Collection<? extends PermissionBlueprintInfo> blueprintInfo) {
        List<HostedTestPermission> convertedPermissions = new ArrayList<>();
        if (blueprintInfo == null) {
            return convertedPermissions;
        }

        //TODO: Can we improve this?
        for (PermissionBlueprintInfo blueprint : blueprintInfo) {
            convertedPermissions.add(new HostedTestPermission(blueprint));
        }

        return convertedPermissions;
    }
}

