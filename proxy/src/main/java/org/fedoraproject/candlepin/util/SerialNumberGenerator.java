/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.util;

import java.io.Serializable;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.Random;

import org.hibernate.HibernateException;
import org.hibernate.engine.SessionImplementor;
import org.hibernate.id.IdentifierGenerator;

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
        long id = 0;
        try {
            long time = System.currentTimeMillis();
            byte[] ipaddress = Inet4Address.getLocalHost().getAddress();
            Random random = new SecureRandom();
            id = time * ipaddress[3] * random.nextInt(255);
        }
        catch (UnknownHostException ex) {
            ex.printStackTrace();
        }
        return Math.abs(id);
    }

}
