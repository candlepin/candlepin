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

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.model.User;
import org.candlepin.util.Util;

import java.util.Date;

/**
 * The UserDTOTranslator provides translation from UserDTO to User model objects.
 */
public class UserDTOTranslator implements ObjectTranslator<UserDTO, User> {

    @Override
    public User translate(UserDTO source) {
        return this.translate(null, source);
    }

    @Override
    public User translate(ModelTranslator modelTranslator, UserDTO source) {
        return source != null ? this.populate(modelTranslator, source, new User()) : null;
    }

    @Override
    public User populate(UserDTO source, User destination) {
        return this.populate(null, source, destination);
    }

    @Override
    public User populate(ModelTranslator modelTranslator, UserDTO source, User destination) {
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
        destination.setUsername(source.getUsername());
        destination.setSuperAdmin(source.getSuperAdmin() == null ?
            false : source.getSuperAdmin());
        destination.setHashedPassword(source.getPassword() != null ?
            Util.hash(source.getPassword()) : null);

        return destination;
    }
}
