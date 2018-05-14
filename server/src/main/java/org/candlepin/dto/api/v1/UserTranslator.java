/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
import org.candlepin.dto.TimestampedEntityTranslator;
import org.candlepin.model.User;



/**
 * The UserTranslator provides translation from User model objects to UserDTOs
 */
public class UserTranslator extends TimestampedEntityTranslator<User, UserDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public UserDTO translate(User source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UserDTO translate(ModelTranslator translator, User source) {
        return source != null ? this.populate(translator, source, new UserDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UserDTO populate(User source, UserDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UserDTO populate(ModelTranslator translator, User source, UserDTO dest) {
        dest = super.populate(translator, source, dest);

        dest.setId(source.getId());
        dest.setUsername(source.getUsername());
        dest.setSuperAdmin(source.isSuperAdmin());

        // The password field should never be set when populating from an entity
        dest.setPassword(null);

        return dest;
    }
}
