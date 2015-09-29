/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.subservice.util;

import org.candlepin.common.util.Util;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.id.IdentifierGenerator;

import java.io.Serializable;

// TODO:
// Perhaps we should just import the one from CP directly? Requires another artifact or refactoring
// a bit on that side to avoid duplication. Can't move this up to common since we don't want to
// force a reliance on Hibernate at that level (probably).


/**
 * SerialNumberGenerator
 */
public class SerialNumberGenerator implements IdentifierGenerator {

    /*
     * (non-Javadoc)
     * @seeorg.hibernate.id.IdentifierGenerator#generate(org.hibernate.engine.
     * SessionImplementor, java.lang.Object)
     */
    @Override
    public Serializable generate(SessionImplementor arg0, Object arg1)
        throws HibernateException {
        return Util.generateUniqueLong();
    }

}
