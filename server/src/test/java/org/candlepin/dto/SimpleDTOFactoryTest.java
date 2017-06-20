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
public class SimpleDTOFactoryTest {

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
     * Test EntityTranslator to use for testing factory functionality
     */
    private static class TestTranslator implements EntityTranslator {
        public TestDTO translate(ModelEntity source) {
            return this.translate(null, source);
        }

        public TestDTO translate(DTOFactory factory, ModelEntity source) {
            return new TestDTO(factory, this, source);
        }

        public TestDTO populate(ModelEntity source, CandlepinDTO dest) {
            return this.populate(null, source, dest);
        }

        public TestDTO populate(DTOFactory factory, ModelEntity source, CandlepinDTO dest) {
            if (!(dest instanceof TestDTO)) {
                throw new IllegalArgumentException("dest is null or not a TestDTO instance");
            }

            TestDTO output = (TestDTO) dest;

            output.setFactory(factory);
            output.setTranslator(this);
            output.setEntity(source);

            return output;
        }
    }


    @Test
    public void testRegisterNewTranslator() {
        DTOFactory factory = new SimpleDTOFactory();
        EntityTranslator translator = new TestTranslator();
        EntityTranslator output;

        output = factory.getTranslator(ModelEntity.class);
        assertNull(output);

        output = factory.registerTranslator(ModelEntity.class, translator);
        assertNull(output);

        output = factory.getTranslator(ModelEntity.class);
        assertSame(output, translator);
    }

    @Test
    public void testReplaceExistingTranslator() {
        DTOFactory factory = new SimpleDTOFactory();
        EntityTranslator translator1 = new TestTranslator();
        EntityTranslator translator2 = new TestTranslator();
        EntityTranslator output;

        output = factory.registerTranslator(ModelEntity.class, translator1);
        assertNull(output);

        output = factory.getTranslator(ModelEntity.class);
        assertSame(output, translator1);

        output = factory.registerTranslator(ModelEntity.class, translator2);
        assertSame(output, translator1);

        output = factory.getTranslator(ModelEntity.class);
        assertSame(output, translator2);
    }

    @Test
    public void testRegisterMultipleTranslators() {
        DTOFactory factory = new SimpleDTOFactory();
        EntityTranslator translator1 = new TestTranslator();
        EntityTranslator translator2 = new TestTranslator();
        EntityTranslator output;

        output = factory.getTranslator(ModelEntity.class);
        assertNull(output);

        output = factory.getTranslator(TestModelEntity.class);
        assertNull(output);

        // Register first translator & check state
        output = factory.registerTranslator(ModelEntity.class, translator1);
        assertNull(output);

        output = factory.getTranslator(ModelEntity.class);
        assertSame(output, translator1);

        output = factory.getTranslator(TestModelEntity.class);
        assertNull(output);

        // Register second translator & check state
        output = factory.registerTranslator(TestModelEntity.class, translator2);
        assertNull(output);

        output = factory.getTranslator(ModelEntity.class);
        assertSame(output, translator1);

        output = factory.getTranslator(TestModelEntity.class);
        assertSame(output, translator2);
    }

    @Test
    public void testUnregisterTranslator() {
        DTOFactory factory = new SimpleDTOFactory();
        EntityTranslator translator = new TestTranslator();
        EntityTranslator output;

        output = factory.registerTranslator(ModelEntity.class, translator);
        assertNull(output);

        output = factory.getTranslator(ModelEntity.class);
        assertSame(output, translator);

        output = factory.unregisterTranslator(ModelEntity.class);
        assertSame(output, translator);

        output = factory.getTranslator(ModelEntity.class);
        assertNull(output);
    }

    @Test
    public void testUnregisterNonexistentTranslator() {
        DTOFactory factory = new SimpleDTOFactory();
        EntityTranslator output;

        output = factory.getTranslator(ModelEntity.class);
        assertNull(output);

        // Note that this is not an error case!
        output = factory.unregisterTranslator(ModelEntity.class);
        assertNull(output);

        output = factory.getTranslator(ModelEntity.class);
        assertNull(output);
    }

    @Test
    public void testBuildDTOWithTranslator() {
        DTOFactory factory = new SimpleDTOFactory();
        EntityTranslator translator = new TestTranslator();
        EntityTranslator output;

        output = factory.registerTranslator(ModelEntity.class, translator);
        assertNull(output);

        ModelEntity entity = new TestModelEntity() {};
        TestDTO dto = factory.<ModelEntity, TestDTO>buildDTO(entity);

        assertNotNull(dto);
        assertSame(factory, dto.getFactory());
        assertSame(translator, dto.getTranslator());
        assertSame(entity, dto.getEntity());
    }

    @Test(expected = DTOException.class)
    public void testBuildDTOWithoutTranslator() {
        DTOFactory factory = new SimpleDTOFactory();
        EntityTranslator translator = new TestTranslator();

        ModelEntity entity = new TestModelEntity() {};
        TestDTO dto = factory.<ModelEntity, TestDTO>buildDTO(entity);
    }

    @Test(expected = DTOException.class)
    public void testBuildDTOWithWrongTranslator() {
        DTOFactory factory = new SimpleDTOFactory();
        EntityTranslator translator = new TestTranslator();

        EntityTranslator output;

        output = factory.registerTranslator(TestModelEntityB.class, translator);
        assertNull(output);

        ModelEntity entity = new TestModelEntity() {};
        TestDTO dto = factory.<ModelEntity, TestDTO>buildDTO(entity);
    }

    @Test
    public void testBuildDTOUsingNearestTranslator() {
        DTOFactory factory = new SimpleDTOFactory();
        EntityTranslator translator1 = new TestTranslator();
        EntityTranslator translator2 = new TestTranslator();
        EntityTranslator translator3 = new TestTranslator();

        factory.registerTranslator(TestModelEntity.class, translator1);
        factory.registerTranslator(ModelEntity.class, translator2);
        factory.registerTranslator(TestModelEntityB.class, translator3);

        ModelEntity entity = new TestModelEntity() {};
        TestDTO dto = factory.<ModelEntity, TestDTO>buildDTO(entity);

        assertNotNull(dto);
        assertSame(factory, dto.getFactory());
        assertSame(translator1, dto.getTranslator());
        assertSame(entity, dto.getEntity());
    }

}
