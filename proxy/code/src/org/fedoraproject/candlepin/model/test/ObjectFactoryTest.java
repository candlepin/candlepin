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
package org.fedoraproject.candlepin.model.test;

import org.fedoraproject.candlepin.model.BaseModel;
import org.fedoraproject.candlepin.model.ObjectFactory;
import org.fedoraproject.candlepin.model.Owner;

import java.util.List;

import junit.framework.TestCase;


/**
 * ObjectFactoryTest
 */
public class ObjectFactoryTest extends TestCase {

    public void testGet() {
        ObjectFactory o1 = ObjectFactory.get();
        ObjectFactory o2 = ObjectFactory.get();
        
        assertNotNull(o1);
        assertNotNull(o2);
        assertEquals(o1, o2);
    }
    
    public void testListObjectsByClass() {
        List<Object> l = ObjectFactory.get().listObjectsByClass(Object.class);
        assertNotNull(l);
        assertTrue(l.isEmpty());
        
        l = ObjectFactory.get().listObjectsByClass(Owner.class);
        assertNotNull(l);
        assertFalse(l.isEmpty());
        Object o = l.get(0);
        assertNotNull(o);
        assertEquals(o.getClass(), Owner.class);
    }
    
    public void testStore() {
        // make sure we don't have one stored already
        List<Object> list = ObjectFactory.get().listObjectsByClass(Long.class);
        assertNotNull(list);
        assertTrue(list.isEmpty());
        
        Long l = new Long(10);
        ObjectFactory.get().store(l);
        
        // verify it got stored
        list = ObjectFactory.get().listObjectsByClass(Long.class);
        assertNotNull(list);
        assertFalse(list.isEmpty());
        Long l2 = (Long) list.get(0);
        assertEquals(l, l2);
    }
    
    public void testLookupByUUID() {
        String uuid = BaseModel.generateUUID();
        assertNull(ObjectFactory.get().lookupByUUID(Owner.class, uuid));
        
        Owner owner = new Owner(uuid);
        owner.setName("unit-test-owner");
        ObjectFactory.get().store(owner);
        Object o = ObjectFactory.get().lookupByUUID(Owner.class, uuid);
        assertNotNull(o);
        assertEquals(o.getClass(), Owner.class);
        assertEquals(((Owner)o).getUuid(), owner.getUuid());
    }
    
    public void testLookupByFieldName() {
        String uuid = BaseModel.generateUUID();
        assertNull(ObjectFactory.get().lookupByUUID(Owner.class, uuid));
        
        Owner owner = new Owner(uuid);
        owner.setName("unit-test-org");
        ObjectFactory.get().store(owner);
        
        Owner o2 = (Owner) ObjectFactory.get().lookupByFieldName(
                Owner.class, "uuid", uuid);
        assertNotNull(o2);
        assertEquals(owner, o2);
    }
}
