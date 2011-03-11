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
package org.fedoraproject.candlepin.resource.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.resource.MigrationResource;

import org.junit.Before;
import org.junit.Test;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Locale;


/**
 * MigrateResourceTest
 */
public class MigrateResourceTest {

    private MigrationResource resource;
    private I18n i18n;
    
    @Before
    public void init() {
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        resource = new MigrationResource(i18n);
    }
    
    @Test
    public void create() {
        JobDetail detail = resource.createMigration("owner", "admin",
            "http://localhost", true);
        assertNotNull(detail);
        JobDataMap map = detail.getJobDataMap();
        assertNotNull(map);
        assertEquals("admin", map.get("owner_key"));
        assertEquals("http://localhost/candlepin", map.get("uri"));
        assertTrue(map.getBoolean("delete"));
    }
    
    @Test(expected = BadRequestException.class)
    public void nullEntity() {
        resource.createMigration(null, "key", "uri", false);
    }
    
    @Test(expected = BadRequestException.class)
    public void invalidEntity() {
        resource.createMigration("badentity", "key", "url", false);
    }
    
}
