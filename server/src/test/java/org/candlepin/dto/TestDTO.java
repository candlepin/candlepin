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



/**
 * A simple test DTO class to use during testing
 */
public class TestDTO extends CandlepinDTO {
    protected DTOFactory factory;
    protected EntityTranslator translator;
    protected ModelEntity entity;

    public TestDTO() {
        // Intentionally left empty
    }

    public TestDTO(DTOFactory factory, EntityTranslator translator, ModelEntity entity) {
        this.setFactory(factory);
        this.setTranslator(translator);
        this.setEntity(entity);
    }

    public DTOFactory getFactory() {
        return this.factory;
    }

    public TestDTO setFactory(DTOFactory factory) {
        this.factory = factory;
        return this;
    }

    public EntityTranslator getTranslator() {
        return this.translator;
    }

    public TestDTO setTranslator(EntityTranslator translator) {
        this.translator = translator;
        return this;
    }

    public ModelEntity getEntity() {
        return this.entity;
    }

    public TestDTO setEntity(ModelEntity entity) {
        this.entity = entity;
        return this;
    }

    public int hashCode() {
        return ((Object) this).hashCode();
    }

    public boolean equals(Object comp) {
        return ((Object) this).equals(comp);
    }
}
