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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;



/**
 * Base test suite for the EntityTranslator subclasses
 */
@RunWith(JUnitParamsRunner.class)
public abstract class AbstractTranslatorTest
    <S extends ModelEntity, D extends CandlepinDTO, T extends EntityTranslator<S, D>> {

    protected DTOFactory factory;
    protected T translator;
    protected S source;
    protected D dest;

    @Before
    public void init() {
        this.factory = new SimpleDTOFactory();

        this.initFactory(this.factory);
        this.translator = this.initTranslator();
        this.source = this.initSourceEntity();
        this.dest = this.initDestDTO();
    }

    protected abstract void initFactory(DTOFactory factory);
    protected abstract T initTranslator();
    protected abstract S initSourceEntity();
    protected abstract D initDestDTO();

    /**
     * Called to verify the DTO contains the information from the given source object. If the
     * childrenGenerated parameter is true, any nested/children objects in the DTO should be
     * verified as well; otherwise, if childrenGenerated is false, nested objects should be null.
     *
     * @param src
     *  The source object used in the translate or populate step
     *
     * @param dto
     *  The generated or populated DTO to verify
     *
     * @param childrenGenerated
     *  Whether or not children were generated/populated for the given DTO
     */
    protected abstract void verifyDTO(S source, D dto, boolean childrenGenerated);

    @Test
    public void testTranslate() {
        D dto = (D) this.translator.translate(this.source);
        this.verifyDTO(this.source, dto, false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTranslateWithNullSource() {
        D dto = (D) this.translator.translate(null);
    }

    @Test
    public void testTranslateWithFactory() {
        D dto = (D) this.translator.translate(this.factory, this.source);
        this.verifyDTO(this.source, dto, true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTranslateWithFactoryAndNullSource() {
        D dto = (D) this.translator.translate(this.factory, null);
    }

    @Test
    public void testPopulate() {
        D dto = (D) this.translator.populate(this.source, this.dest);
        assertSame(dto, this.dest);
        this.verifyDTO(this.source, this.dest, false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPopulateWithNullSource() {
        D dto = (D) this.translator.populate(null, this.dest);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPopulateWithNullDestination() {
        D dto = (D) this.translator.populate(this.source, null);
    }

    @Test
    public void testPopulateWithFactory() {
        D dto = (D) this.translator.populate(this.factory, this.source, this.dest);
        assertSame(dto, this.dest);
        this.verifyDTO(this.source, this.dest, true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPopulateWithFactoryAndNullSource() {
        D dto = (D) this.translator.populate(this.factory, null, this.dest);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPopulateWithFactoryAndNullDestination() {
        D dto = (D) this.translator.populate(this.factory, this.source, null);
    }
}
