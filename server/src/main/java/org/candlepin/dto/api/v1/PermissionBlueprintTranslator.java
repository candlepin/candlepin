/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
import org.candlepin.auth.permissions.PermissionFactory.PermissionType;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.TimestampedEntityTranslator;
import org.candlepin.model.PermissionBlueprint;



/**
 * The PermissionBlueprintTranslator provides translation from PermissionBlueprint model objects to
 * PermissionBlueprintDTOs
 */
public class PermissionBlueprintTranslator extends
    TimestampedEntityTranslator<PermissionBlueprint, PermissionBlueprintDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public PermissionBlueprintDTO translate(PermissionBlueprint source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PermissionBlueprintDTO translate(ModelTranslator translator, PermissionBlueprint source) {
        return source != null ? this.populate(translator, source, new PermissionBlueprintDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PermissionBlueprintDTO populate(PermissionBlueprint source, PermissionBlueprintDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PermissionBlueprintDTO populate(ModelTranslator translator, PermissionBlueprint source,
        PermissionBlueprintDTO dest) {

        dest = super.populate(translator, source, dest);

        PermissionType type = source.getType();
        Access access = source.getAccess();

        dest.setId(source.getId());
        dest.setType(type != null ? type.name() : null);
        dest.setAccess(access != null ? access.name() : null);

        if (translator != null) {
            dest.setOwner(translator.translate(source.getOwner(), OwnerDTO.class));
        }
        else {
            dest.setOwner(null);
        }

        return dest;
    }

}
