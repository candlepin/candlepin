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

import static org.junit.Assert.assertTrue;

import org.fedoraproject.candlepin.model.Status;
import org.fedoraproject.candlepin.resource.StatusResource;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;

import org.junit.Before;
import org.junit.Test;

/**
 * StatusResourceTest
 */
public class StatusResourceTest extends DatabaseTestFixture  {
    
    private StatusResource statusResource;

    @Before
    public void createObjects() throws Exception {
        statusResource = new StatusResource();
    }
    
    @Test
    public void testVersionIsKnown() throws Exception {
        Status status = statusResource.status();
        assertTrue("The vesrsion should be known", !"Unknown".equals(status.getVersion()));
        assertTrue("The vesrsion should be filled in", !"${version}"
            .equals(status.getVersion()));        
    }
    
    @Test
    public void testReleaseIsKnown() throws Exception {
        Status status = statusResource.status();
        assertTrue("The hash should be known", !"Unkown".equals(status.getRelease()));
        assertTrue("The hash should be filled in", !"${hash}".equals(status.getRelease())); 
    }    
}
