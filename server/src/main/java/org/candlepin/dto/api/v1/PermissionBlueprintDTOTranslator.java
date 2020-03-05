/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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

package org.candlepin.dto.api.v1;

import org.candlepin.auth.Access;
import org.candlepin.auth.permissions.PermissionFactory;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.model.Owner;
import org.candlepin.model.PermissionBlueprint;

import java.util.Date;

/**
 * The PermissionBlueprintDTOTranslator provides translation
 * from PermissionBlueprintDTO to PermissionBlueprint model objects.
 */
public class PermissionBlueprintDTOTranslator implements
    ObjectTranslator<PermissionBlueprintDTO, PermissionBlueprint> {

    @Override
    public PermissionBlueprint translate(PermissionBlueprintDTO source) {
        return this.translate(null, source);
    }

    @Override
    public PermissionBlueprint translate(ModelTranslator modelTranslator, PermissionBlueprintDTO source) {
        return source != null ?
            this.populate(modelTranslator, source, new PermissionBlueprint()) : null;
    }

    @Override
    public PermissionBlueprint populate(PermissionBlueprintDTO source, PermissionBlueprint destination) {
        return this.populate(null, source, destination);
    }

    @Override
    public PermissionBlueprint populate(ModelTranslator modelTranslator,
        PermissionBlueprintDTO source, PermissionBlueprint destination) {

        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }

        destination.setCreated(source.getCreated() != null ?
            new Date(source.getCreated().toInstant().toEpochMilli()) : null);
        destination.setUpdated(source.getUpdated() != null ?
            new Date(source.getUpdated().toInstant().toEpochMilli()) : null);
        destination.setId(source.getId());
        destination.setType(source.getType() == null ? null :
            PermissionFactory.PermissionType.valueOf(source.getType()));
        destination.setAccess(source.getAccess() == null ? null :
            Access.valueOf(source.getAccess()));

        if (modelTranslator != null) {
            destination.setOwner(modelTranslator.translate(source.getOwner(), Owner.class));
        }
        else {
            destination.setOwner(null);
        }

        return destination;
    }
}
