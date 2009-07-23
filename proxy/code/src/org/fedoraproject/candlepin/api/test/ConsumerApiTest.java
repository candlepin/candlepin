/**
 * Copyright (c) 2008 Red Hat, Inc.
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
package org.fedoraproject.candlepin.api.test;

import com.sun.jersey.api.representation.Form;

import org.fedoraproject.candlepin.api.ConsumerApi;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ObjectFactory;

import junit.framework.TestCase;


/**
 * ConsumerApiTest
 * @version $Rev$
 */
public class ConsumerApiTest extends TestCase {

    public void testCreateConsumer() throws Exception {
        String newname = "test-consumer-" + System.currentTimeMillis();
        
        ConsumerApi capi = new ConsumerApi();
        Form f = new Form();
        f.add("name", newname);
        f.add("type", "standard-system");
        capi.create(f);
        assertNotNull(ObjectFactory.get().lookupByFieldName(Consumer.class, 
                "name", newname));
        
        
    }
}
