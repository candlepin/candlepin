/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.dto;

import org.candlepin.model.ModelEntity;

import static org.junit.Assert.*;

import junitparams.JUnitParamsRunner;

import org.junit.Test;
import org.junit.runner.RunWith;



/**
 * Test suite for the ProductData class
 */
@RunWith(JUnitParamsRunner.class)
public class SimpleModelTranslatorTest {

    private static class TestDTOSubclass extends TestDTO {
        // No additional functionality necessary
    }

    private interface TestModelEntity extends ModelEntity {
        // Intentionally left empty
    }

    private interface TestModelEntityB extends ModelEntity {
        // Intentionally left empty
    }

    /**
     * Test ObjectTranslator to use for testing translation functionality
     */
    private static class TestTranslator implements ObjectTranslator<Object, TestDTO> {
        public TestDTO translate(Object source) {
            return this.translate(null, source);
        }

        public TestDTO translate(ModelTranslator modelTranslator, Object source) {
            return new TestDTO(modelTranslator, this, source);
        }

        public TestDTO populate(Object source, TestDTO dest) {
            return this.populate(null, source, dest);
        }

        public TestDTO populate(ModelTranslator modelTranslator, Object source, TestDTO dest) {
            if (dest == null) {
                throw new IllegalArgumentException("dest is null");
            }

            dest.setModelTranslator(modelTranslator);
            dest.setObjectTranslator(this);
            dest.setSourceObject(source);

            return dest;
        }
    }


    @Test
    public void testRegisterNewTranslator() {
        ModelTranslator modelTranslator = new SimpleModelTranslator();
        ObjectTranslator translator = new TestTranslator();
        ObjectTranslator output;

        output = modelTranslator.getTranslator(ModelEntity.class);
        assertNull(output);

        output = modelTranslator.registerTranslator(ModelEntity.class, translator);
        assertNull(output);

        output = modelTranslator.getTranslator(ModelEntity.class);
        assertSame(output, translator);
    }

    @Test
    public void testReplaceExistingTranslator() {
        ModelTranslator modelTranslator = new SimpleModelTranslator();
        ObjectTranslator translator1 = new TestTranslator();
        ObjectTranslator translator2 = new TestTranslator();
        ObjectTranslator output;

        output = modelTranslator.registerTranslator(ModelEntity.class, translator1);
        assertNull(output);

        output = modelTranslator.getTranslator(ModelEntity.class);
        assertSame(output, translator1);

        output = modelTranslator.registerTranslator(ModelEntity.class, translator2);
        assertSame(output, translator1);

        output = modelTranslator.getTranslator(ModelEntity.class);
        assertSame(output, translator2);
    }

    @Test
    public void testRegisterMultipleTranslators() {
        ModelTranslator modelTranslator = new SimpleModelTranslator();
        ObjectTranslator translator1 = new TestTranslator();
        ObjectTranslator translator2 = new TestTranslator();
        ObjectTranslator output;

        output = modelTranslator.getTranslator(ModelEntity.class);
        assertNull(output);

        output = modelTranslator.getTranslator(TestModelEntity.class);
        assertNull(output);

        // Register first translator & check state
        output = modelTranslator.registerTranslator(ModelEntity.class, translator1);
        assertNull(output);

        output = modelTranslator.getTranslator(ModelEntity.class);
        assertSame(output, translator1);

        output = modelTranslator.getTranslator(TestModelEntity.class);
        assertNull(output);

        // Register second translator & check state
        output = modelTranslator.registerTranslator(TestModelEntity.class, translator2);
        assertNull(output);

        output = modelTranslator.getTranslator(ModelEntity.class);
        assertSame(output, translator1);

        output = modelTranslator.getTranslator(TestModelEntity.class);
        assertSame(output, translator2);
    }

    @Test
    public void testUnregisterTranslator() {
        ModelTranslator modelTranslator = new SimpleModelTranslator();
        ObjectTranslator translator = new TestTranslator();
        ObjectTranslator output;

        output = modelTranslator.registerTranslator(ModelEntity.class, translator);
        assertNull(output);

        output = modelTranslator.getTranslator(ModelEntity.class);
        assertSame(output, translator);

        output = modelTranslator.unregisterTranslator(ModelEntity.class);
        assertSame(output, translator);

        output = modelTranslator.getTranslator(ModelEntity.class);
        assertNull(output);
    }

    @Test
    public void testUnregisterNonexistentTranslator() {
        ModelTranslator modelTranslator = new SimpleModelTranslator();
        ObjectTranslator output;

        output = modelTranslator.getTranslator(ModelEntity.class);
        assertNull(output);

        // Note that this is not an error case!
        output = modelTranslator.unregisterTranslator(ModelEntity.class);
        assertNull(output);

        output = modelTranslator.getTranslator(ModelEntity.class);
        assertNull(output);
    }

    @Test
    public void testTranslateWithTranslator() {
        ModelTranslator modelTranslator = new SimpleModelTranslator();
        ObjectTranslator translator = new TestTranslator();
        ObjectTranslator output;

        output = modelTranslator.registerTranslator(ModelEntity.class, translator);
        assertNull(output);

        ModelEntity entity = new TestModelEntity() {};
        TestDTO dto = modelTranslator.<ModelEntity, TestDTO>translate(entity);

        assertNotNull(dto);
        assertSame(modelTranslator, dto.getModelTranslator());
        assertSame(translator, dto.getObjectTranslator());
        assertSame(entity, dto.getSourceObject());
    }

    @Test(expected = TranslationException.class)
    public void testTranslateWithoutTranslator() {
        ModelTranslator modelTranslator = new SimpleModelTranslator();
        ObjectTranslator translator = new TestTranslator();

        ModelEntity entity = new TestModelEntity() {};
        TestDTO dto = modelTranslator.<ModelEntity, TestDTO>translate(entity);
    }

    @Test(expected = TranslationException.class)
    public void testTranslateWithWrongTranslator() {
        ModelTranslator modelTranslator = new SimpleModelTranslator();
        ObjectTranslator translator = new TestTranslator();

        ObjectTranslator output;

        output = modelTranslator.registerTranslator(TestModelEntityB.class, translator);
        assertNull(output);

        ModelEntity entity = new TestModelEntity() {};
        TestDTO dto = modelTranslator.<ModelEntity, TestDTO>translate(entity);
    }

    @Test
    public void testTranslateUsingNearestTranslator() {
        ModelTranslator modelTranslator = new SimpleModelTranslator();
        ObjectTranslator translator1 = new TestTranslator();
        ObjectTranslator translator2 = new TestTranslator();
        ObjectTranslator translator3 = new TestTranslator();

        modelTranslator.registerTranslator(TestModelEntity.class, translator1);
        modelTranslator.registerTranslator(ModelEntity.class, translator2);
        modelTranslator.registerTranslator(TestModelEntityB.class, translator3);

        ModelEntity entity = new TestModelEntity() {};
        TestDTO dto = modelTranslator.<ModelEntity, TestDTO>translate(entity);

        assertNotNull(dto);
        assertSame(modelTranslator, dto.getModelTranslator());
        assertSame(translator1, dto.getObjectTranslator());
        assertSame(entity, dto.getSourceObject());
    }

}
