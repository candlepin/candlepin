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
package org.candlepin.cache;

import com.google.inject.Injector;

/**
 * Configuration of caches in candlepin. This is configuration according to JCache API.
 * Additional, EHCache specific, configurations are in ehcache.xml and ehcache-stats.xml
 * configuration.
 * @author fnguyen
 *
 */
public class CacheContextListener {

    public void contextInitialized(Injector injector) {
    }
}
