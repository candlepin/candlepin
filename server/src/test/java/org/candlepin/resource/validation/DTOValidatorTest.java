/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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
package org.candlepin.resource.validation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.validation.ConstraintViolationException;
import javax.validation.Validation;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Test suite for the DTOValidator class
 */
public class DTOValidatorTest {

    private static DTOValidator validator;

    /**
     * A mock "DTO" object used for testing the DTOValidator
     */
    public static class TestDTO {

        @NotNull
        String field1;

        @Size(min = 1)
        String field2;

        Set<Object> set;
        List<Object> list;
        Queue<Object> queue;
        Stack<Object> stack;
        Map<Object, Object> map1;
        Map<Object, Object> map2;
        Map<Object, Object> map3;
        Map<Object, Object> map4;

        public Set<Object> getSet() {
            return set;
        }

        public List<Object> getList() {
            return list;
        }

        public Queue<Object> getQueue() {
            return queue;
        }

        public Stack<Object> getStack() {
            return stack;
        }

        public Map<Object, Object> getMap1() {
            return map1;
        }

        public Map<Object, Object> getMap2() {
            return map2;
        }

        public Map<Object, Object> getMap3() {
            return map3;
        }

        public Map<Object, Object> getMap4() {
            return map4;
        }
    }

    @BeforeAll
    public static void setUp() {
        validator = new DTOValidator(Validation.buildDefaultValidatorFactory());
    }

    @Test
    public void testNonNullAndNonEmptyFieldsMarkedAsNotNullAndSizeAtLeastOneAreValid() {
        TestDTO dto = new TestDTO();
        dto.field1 = "1";
        dto.field2 = "2";

        assertDoesNotThrow(() ->
            validator.validateConstraints(dto)
        );
    }

    @Test
    public void testEmptyFieldMarkedAsSizeAtLeastOneIsInvalid() {
        TestDTO dto = new TestDTO();
        dto.field1 = "1";
        dto.field2 = "";

        assertThrows(ConstraintViolationException.class, () ->
            validator.validateConstraints(dto)
        );
    }

    @Test
    public void testNullFieldMarkedAsNotNullIsInvalid() {
        TestDTO dto = new TestDTO();
        dto.field1 = null;
        dto.field2 = "2";

        assertThrows(ConstraintViolationException.class, () ->
            validator.validateConstraints(dto)
        );
    }

    @Test
    public void testNullCollectionsAreValid() {
        TestDTO dto = new TestDTO();

        assertDoesNotThrow(() ->
            validator.validateCollectionElementsNotNull(dto::getList, dto::getSet, dto::getQueue,
            dto::getStack)
        );
    }

    @Test
    public void testEmptyCollectionsAreValid() {
        TestDTO dto = new TestDTO();
        dto.set = new HashSet<>();
        dto.list = new ArrayList<>();
        dto.queue = new PriorityQueue<>();
        dto.stack = new Stack<>();

        assertDoesNotThrow(() ->
            validator.validateCollectionElementsNotNull(dto::getList, dto::getSet, dto::getQueue,
            dto::getStack)
        );
    }

    @Test
    public void testCollectionsWithOnlyNonNullItemsAreValid() {
        TestDTO dto = new TestDTO();
        dto.set = new HashSet<>();
        dto.set.add(new Object());
        dto.list = new ArrayList<>();
        dto.list.add(new Object());
        dto.queue = new PriorityQueue<>();
        dto.queue.add(new Object());
        dto.stack = new Stack<>();
        dto.stack.add(new Object());

        assertDoesNotThrow(() ->
            validator.validateCollectionElementsNotNull(dto::getList, dto::getSet, dto::getQueue,
            dto::getStack)
        );
    }

    @Test
    public void testCollectionsWithAtLeastOneNullItemAreInValid() {
        TestDTO dto = new TestDTO();
        dto.set = new HashSet<>();
        dto.set.add(new Object());
        dto.list = new ArrayList<>();
        dto.list.add(new Object());
        dto.queue = new PriorityQueue<>();
        dto.queue.add(new Object());
        dto.stack = new Stack<>();
        dto.stack.add(null);

        assertThrows(IllegalArgumentException.class, () ->
            validator.validateCollectionElementsNotNull(dto::getList, dto::getSet, dto::getQueue,
            dto::getStack)
        );
    }

    @Test
    public void testOneCollectionWithAtLeastOneNullItemIsInValid() {
        TestDTO dto = new TestDTO();
        dto.set = new HashSet<>();
        dto.set.add(null);

        assertThrows(IllegalArgumentException.class, () ->
            validator.validateCollectionElementsNotNull(dto::getSet)
        );
    }

    @Test
    public void testTwoCollectionsWithAtLeastOneNullItemAreInValid() {
        TestDTO dto = new TestDTO();
        dto.set = new HashSet<>();
        dto.set.add(new Object());
        dto.list = new ArrayList<>();
        dto.list.add(null);

        assertThrows(IllegalArgumentException.class, () ->
            validator.validateCollectionElementsNotNull(dto::getSet, dto::getList)
        );
    }

    @Test
    public void testThreeCollectionsWithAtLeastOneNullItemAreInValid() {
        TestDTO dto = new TestDTO();
        dto.set = new HashSet<>();
        dto.set.add(new Object());
        dto.list = new ArrayList<>();
        dto.list.add(null);
        dto.queue = new PriorityQueue<>();
        dto.queue.add(new Object());

        assertThrows(IllegalArgumentException.class, () ->
            validator.validateCollectionElementsNotNull(dto::getSet, dto::getList, dto::getQueue)
        );
    }

    @Test
    public void testNullMapsAreValid() {
        TestDTO dto = new TestDTO();

        assertDoesNotThrow(() ->
            validator.validateMapElementsNotNull(dto::getMap1, dto::getMap2, dto::getMap3,
            dto::getMap4)
        );
    }

    @Test
    public void testEmptyMapsAreValid() {
        TestDTO dto = new TestDTO();
        dto.map1 = new HashMap<>();
        dto.map2 = new ConcurrentHashMap<>();
        dto.map3 = new ConcurrentHashMap<>();
        dto.map4 = new WeakHashMap<>();

        assertDoesNotThrow(() ->
            validator.validateMapElementsNotNull(dto::getMap1, dto::getMap2, dto::getMap3,
            dto::getMap4)
        );
    }

    @Test
    public void testMapsWithOnlyNonNullItemsAreValid() {
        TestDTO dto = new TestDTO();
        dto.map1 = new HashMap<>();
        dto.map1.put(new Object(), new Object());
        dto.map2 = new ConcurrentHashMap<>();
        dto.map2.put(new Object(), new Object());
        dto.map3 = new ConcurrentHashMap<>();
        dto.map3.put(new Object(), new Object());
        dto.map4 = new WeakHashMap<>();
        dto.map4.put(new Object(), new Object());

        assertDoesNotThrow(() ->
            validator.validateMapElementsNotNull(dto::getMap1, dto::getMap2, dto::getMap3,
            dto::getMap4)
        );
    }

    @Test
    public void testMapsWithAtLeastOneNullValueAreInValid() {
        TestDTO dto = new TestDTO();
        dto.map1 = new HashMap<>();
        dto.map1.put(new Object(), new Object());
        dto.map2 = new ConcurrentHashMap<>();
        dto.map2.put(new Object(), new Object());
        dto.map3 = new ConcurrentHashMap<>();
        dto.map3.put(new Object(), new Object());
        dto.map4 = new WeakHashMap<>();
        dto.map4.put(new Object(), null);

        assertThrows(IllegalArgumentException.class, () ->
            validator.validateMapElementsNotNull(dto::getMap1, dto::getMap2, dto::getMap3,
            dto::getMap4)
        );
    }

    @Test
    public void testMapsWithAtLeastOneNullKeyAreInValid() {
        TestDTO dto = new TestDTO();
        dto.map1 = new HashMap<>();
        dto.map1.put(new Object(), new Object());
        dto.map2 = new ConcurrentHashMap<>();
        dto.map2.put(new Object(), new Object());
        dto.map3 = new ConcurrentHashMap<>();
        dto.map3.put(new Object(), new Object());
        dto.map4 = new WeakHashMap<>();
        dto.map4.put(null, new Object());

        assertThrows(IllegalArgumentException.class, () ->
            validator.validateMapElementsNotNull(dto::getMap1, dto::getMap2, dto::getMap3,
            dto::getMap4)
        );
    }

    @Test
    public void testOneMapWithAtLeastOneNullValueIsInValid() {
        TestDTO dto = new TestDTO();
        dto.map1 = new HashMap<>();
        dto.map1.put(new Object(), null);

        assertThrows(IllegalArgumentException.class, () ->
            validator.validateMapElementsNotNull(dto::getMap1)
        );
    }

    @Test
    public void testTwoMapsWithAtLeastOneNullValueAreInValid() {
        TestDTO dto = new TestDTO();
        dto.map1 = new HashMap<>();
        dto.map1.put(new Object(), null);
        dto.map2 = new ConcurrentHashMap<>();
        dto.map2.put(new Object(), new Object());

        assertThrows(IllegalArgumentException.class, () ->
            validator.validateMapElementsNotNull(dto::getMap1, dto::getMap2)
        );
    }

    @Test
    public void testThreeMapsWithAtLeastOneNullValueAreInValid() {
        TestDTO dto = new TestDTO();
        dto.map1 = new HashMap<>();
        dto.map1.put(new Object(), null);
        dto.map2 = new ConcurrentHashMap<>();
        dto.map2.put(new Object(), new Object());
        dto.map3 = new ConcurrentHashMap<>();
        dto.map3.put(new Object(), new Object());

        assertThrows(IllegalArgumentException.class, () ->
            validator.validateMapElementsNotNull(dto::getMap1, dto::getMap2, dto::getMap3)
        );
    }
}
