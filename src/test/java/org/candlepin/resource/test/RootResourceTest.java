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
package org.candlepin.resource.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.resource.Link;
import org.candlepin.resource.RootResource;

import org.junit.Test;

import java.util.List;


/**
 * RootResourceTest
 */
public class RootResourceTest {

    @Test
    public void getRootResources() {
        RootResource rr = new RootResource(new CandlepinCommonTestConfig());
        List<Link> links = rr.getRootResources();
        assertNotNull(links);
        for (Link link : links) {
            assertEquals("/" + link.getRel(), link.getHref());
        }
    }
}
