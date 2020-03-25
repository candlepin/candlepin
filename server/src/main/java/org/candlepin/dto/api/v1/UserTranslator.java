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
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.model.User;

import java.time.ZoneOffset;


/**
 * The UserTranslator provides translation from User model objects to UserDTOs
 */
public class UserTranslator implements ObjectTranslator<User, UserDTO> {

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

        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("destination is null");
        }

        dest.created(source.getCreated() != null ?
                source.getCreated().toInstant().atOffset(ZoneOffset.UTC) : null)
            .updated(source.getUpdated() != null ?
                source.getUpdated().toInstant().atOffset(ZoneOffset.UTC) : null)
            .username(source.getUsername())
            .superAdmin(source.isSuperAdmin())
            .password(null) // The password field should never be set when populating from an entity
            .id(source.getId());

        return dest;
    }
}
