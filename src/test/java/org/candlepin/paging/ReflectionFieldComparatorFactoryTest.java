/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.paging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;



public class ReflectionFieldComparatorFactoryTest {

    private <T> ReflectionFieldComparatorFactory<T> buildFactory(Class<T> type, String defaultFieldName) {
        return new ReflectionFieldComparatorFactory<>(type, defaultFieldName);
    }

    private <T> ReflectionFieldComparatorFactory<T> buildFactory(Class<T> type) {
        return this.buildFactory(type, null);
    }

    @Test
    public void testFactoryRequiresTypeAtBuildTime() {
        assertThrows(IllegalArgumentException.class,
            () -> new ReflectionFieldComparatorFactory<>(null, "default field name"));
    }

    @Test
    public void testFactoryPermitsNullDefaultFieldName() {
        assertDoesNotThrow(() -> new ReflectionFieldComparatorFactory<>(Object.class, null));
    }

    @ParameterizedTest
    @ValueSource(strings = { "myField", "MyField" })
    public void testMethodFoundWithGetPrefix(String fieldName) {
        Object obj = new Object() {
            public String getMyField() {
                return "some value";
            }
        };

        ReflectionFieldComparatorFactory<?> factory = this.buildFactory(obj.getClass());
        assertNotNull(factory.getComparator(fieldName));
    }

    @ParameterizedTest
    @ValueSource(strings = { "isPresent", "IsPresent" })
    public void testMethodFoundWithIsPrefix(String fieldName) {
        Object obj = new Object() {
            public Boolean getIsPresent() {
                return true;
            }
        };

        ReflectionFieldComparatorFactory<?> factory = this.buildFactory(obj.getClass());
        assertNotNull(factory.getComparator(fieldName));
    }

    @ParameterizedTest
    @ValueSource(strings = { "hasMoreFields", "HasMoreFields" })
    public void testMethodFoundWithHasPrefix(String fieldName) {
        Object obj = new Object() {
            public Boolean getHasMoreFields() {
                return false;
            }
        };

        ReflectionFieldComparatorFactory<?> factory = this.buildFactory(obj.getClass());
        assertNotNull(factory.getComparator(fieldName));
    }

    @ParameterizedTest
    @ValueSource(strings = { "a", "A" })
    public void testMethodNameMayBeReallyShort(String fieldName) {
        Object obj = new Object() {
            public String getA() {
                return "A";
            }
        };

        ReflectionFieldComparatorFactory<?> factory = this.buildFactory(obj.getClass());
        assertNotNull(factory.getComparator(fieldName));
    }

    @Test
    public void testMethodMustNotRequireParameters() {
        Object obj = new Object() {
            public Boolean hasMethodWithParams(String p1) {
                return true;
            }
        };

        ReflectionFieldComparatorFactory<?> factory = this.buildFactory(obj.getClass());
        assertNull(factory.getComparator("MethodWithParams"));
    }

    @Test
    public void testMethodMustReturnAValue() {
        Object obj = new Object() {
            public void hasVoidMethod() {
                // empty
            }
        };

        ReflectionFieldComparatorFactory<?> factory = this.buildFactory(obj.getClass());
        assertNull(factory.getComparator("VoidMethod"));
    }

    @Test
    public void testMethodMustReturnComparableValue() {
        Object obj = new Object() {
            public Object getNonConformingValue() {
                return true;
            }
        };

        ReflectionFieldComparatorFactory<?> factory = this.buildFactory(obj.getClass());
        assertNull(factory.getComparator("NonConformingValue"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", " ", "   " })
    public void testFactoryIgnoresDefaultForStandardLookup(String emptyFieldName) {
        Object obj = new Object() {
            public String getFieldTwo() {
                return "field two";
            }
        };

        ReflectionFieldComparatorFactory<?> factory = this.buildFactory(obj.getClass(), "FieldTwo");
        assertNull(factory.getComparator(emptyFieldName));
    }

    @ParameterizedTest
    @ValueSource(strings = { "myField", "MyField" })
    public void testDefaultMethodFoundWithGetPrefix(String fieldName) {
        Object obj = new Object() {
            public String getMyField() {
                return "some value";
            }
        };

        ReflectionFieldComparatorFactory<?> factory = this.buildFactory(obj.getClass(), fieldName);
        assertNotNull(factory.getDefaultComparator());
    }

    @ParameterizedTest
    @ValueSource(strings = { "isPresent", "IsPresent" })
    public void testDefaultMethodFoundWithIsPrefix(String fieldName) {
        Object obj = new Object() {
            public Boolean getIsPresent() {
                return true;
            }
        };

        ReflectionFieldComparatorFactory<?> factory = this.buildFactory(obj.getClass(), fieldName);
        assertNotNull(factory.getDefaultComparator());
    }

    @ParameterizedTest
    @ValueSource(strings = { "hasMoreFields", "HasMoreFields" })
    public void testDefaultMethodFoundWithHasPrefix(String fieldName) {
        Object obj = new Object() {
            public Boolean getHasMoreFields() {
                return false;
            }
        };

        ReflectionFieldComparatorFactory<?> factory = this.buildFactory(obj.getClass(), fieldName);
        assertNotNull(factory.getDefaultComparator());
    }

    @ParameterizedTest
    @ValueSource(strings = { "a", "A" })
    public void testDefaultMethodNameMayBeReallyShort(String fieldName) {
        Object obj = new Object() {
            public String getA() {
                return "A";
            }
        };

        ReflectionFieldComparatorFactory<?> factory = this.buildFactory(obj.getClass(), fieldName);
        assertNotNull(factory.getDefaultComparator());
    }

    @Test
    public void testDefaultMethodMustNotRequireParameters() {
        Object obj = new Object() {
            public Boolean hasMethodWithParams(String p1) {
                return true;
            }
        };

        ReflectionFieldComparatorFactory<?> factory = this.buildFactory(obj.getClass(), "MethodWithParams");
        assertNull(factory.getDefaultComparator());
    }

    @Test
    public void testDefaultMethodMustReturnAValue() {
        Object obj = new Object() {
            public void hasVoidMethod() {
                // empty
            }
        };

        ReflectionFieldComparatorFactory<?> factory = this.buildFactory(obj.getClass(), "VoidMethod");
        assertNull(factory.getDefaultComparator());
    }

    @Test
    public void testDefaultMethodMustReturnComparableValue() {
        Object obj = new Object() {
            public Object getNonConformingValue() {
                return true;
            }
        };

        ReflectionFieldComparatorFactory<?> factory = this.buildFactory(obj.getClass(), "NonConformingValue");
        assertNull(factory.getDefaultComparator());
    }

}
