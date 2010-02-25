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
package org.fedoraproject.candlepin.config;


/**
 * JPAConfiguration
 * @version $Rev$
 */
class JPAConfiguration extends ConfigurationParser {
    /** JPA configuration prefix */
    public static final String JPA_CONFIG_PREFIX = "jpa.config";
    /** Length of the <code>JPA_CONFIG_PREFIX</code> */
    public static final int PREFIX_LENGTH = JPA_CONFIG_PREFIX.length();
    
    /** hibernate connection url */
    public static final String URL_CONFIG = "hibernate.connection.url";
    /** Comment for <code>USER_CONFIG</code> */
    public static final String USER_CONFIG = "hibernate.connection.username";
    /** Comment for <code>PASSWORD_CONFIG</code> */
    public static final String PASSWORD_CONFIG = "hibernate.connection.password";
    
    public int prefixLength() {
        return PREFIX_LENGTH;
    }
}
