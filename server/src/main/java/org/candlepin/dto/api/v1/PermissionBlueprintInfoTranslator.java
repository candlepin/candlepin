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

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.service.model.PermissionBlueprintInfo;

/**
 * The PermissionBlueprintInfoTranslator provides translation from PermissionBlueprintInfo service
 * model objects to PermissionBlueprintDTOs
 */
public class PermissionBlueprintInfoTranslator implements
    ObjectTranslator<PermissionBlueprintInfo, PermissionBlueprintDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public PermissionBlueprintDTO translate(PermissionBlueprintInfo source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PermissionBlueprintDTO translate(ModelTranslator translator, PermissionBlueprintInfo source) {
        return source != null ? this.populate(translator, source, new PermissionBlueprintDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PermissionBlueprintDTO populate(PermissionBlueprintInfo source, PermissionBlueprintDTO dest) {
        return this.populate(null, source, dest);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PermissionBlueprintDTO populate(ModelTranslator translator, PermissionBlueprintInfo source,
        PermissionBlueprintDTO dest) {

        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("dest is null");
        }

        // Service model objects do not provide an ID
        dest.setId(null);
        dest.setType(source.getTypeName());
        dest.setAccess(source.getAccessLevel());

        if (source.getOwner() != null) {
            dest.setOwner(new NestedOwnerDTO().key(source.getOwner().getKey()));
        }
        else {
            dest.setOwner(null);
        }

        return dest;
    }

}
