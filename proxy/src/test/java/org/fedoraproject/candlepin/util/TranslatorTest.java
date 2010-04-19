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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TranslatorTest
 */
public class TranslatorTest {

    @Test
    public void int2List() {
        List<Integer> l = Translator.int2List(10);
        assertNotNull(l);
        assertEquals(1, l.size());
        assertEquals(new Integer(10), l.get(0));
        
        l = Translator.int2List(null);
        assertTrue(l.isEmpty());
    }
    
    @Test
    public void int2Boolean() {
        assertFalse(Translator.int2Boolean(null));
        assertFalse(Translator.int2Boolean(10));
        assertFalse(Translator.int2Boolean(0));
        assertTrue(Translator.int2Boolean(1));
    }
    
    @Test
    public void int2String() {
        assertEquals("42", Translator.int2String(42));
        assertEquals("", Translator.int2String(null));
    }

    @Test
    public void bigDecimal2SomethingElse() throws Exception {
        BigDecimal bd = new BigDecimal(1);
        int i = Translator.bigDecimal2Int(bd);
        assertEquals(1, i);
        
        Integer bi = Translator.bigDecimal2IntObject(bd);
        assertEquals(new Integer(1), bi);
        
        long l = Translator.bigDecimal2Long(bd);
        assertEquals(1, l);
        
        Long bl = Translator.bigDecimal2LongObj(bd);
        assertEquals(new Long(1), bl);
    }
    
    @Test
    public void double2SomethingElse() {
        assertEquals("10.0", Translator.double2String(10.0));
        assertEquals("0", Translator.double2String(null));
    }
    
    @Test
    public void long2SomethingElse() throws Exception {
        assertEquals(10L, Translator.long2Objlong(10L));
    }

    @Test
    public void string2SomethingElse() throws Exception {
        assertFalse(Translator.string2boolean(null));
        assertTrue(Translator.string2boolean("Y"));
        assertTrue(Translator.string2boolean("y"));
        assertTrue(Translator.string2boolean("1"));
        assertTrue(Translator.string2boolean("true"));
        assertTrue(Translator.string2boolean("tRUe"));
        assertFalse(Translator.string2boolean("0"));
        assertFalse(Translator.string2boolean("f"));
        assertFalse(Translator.string2boolean("F"));
        assertFalse(Translator.string2boolean("n"));
        assertFalse(Translator.string2boolean("false"));
        assertFalse(Translator.string2boolean("faLSe"));
        assertFalse(Translator.string2boolean("rock on"));
    }
    
    @Test
    public void date2String() {
        Date now = new Date();
        assertEquals(now.toString(), Translator.date2String(now));
        assertEquals("", Translator.date2String(null));
    }
    
    @Test
    public void boolean2Somethingelse() {
        assertTrue(Translator.boolean2boolean(Boolean.TRUE));
        assertFalse(Translator.boolean2boolean(Boolean.FALSE));
        assertFalse(Translator.boolean2boolean(null));
        
        assertEquals("false", Translator.boolean2String(Boolean.FALSE));
        assertEquals("true", Translator.boolean2String(Boolean.TRUE));
        assertEquals("false", Translator.boolean2String(null));
    }
    
    @Test
    public void map2String() {
        Map<Object, Object> foo = new HashMap<Object, Object>();
        foo.put("bar", "nut");
        foo.put("java", "sucks");
        assertEquals(foo.toString(), Translator.map2String(foo));
        assertEquals("", Translator.map2String(null));
    }
    
    @Test
    public void list2String() {
        List<Object> l = new ArrayList<Object>();
        l.add(1);
        l.add(2);
        assertEquals(l.toString(), Translator.list2String(l));
        assertEquals("", Translator.list2String(null));
    }
}
