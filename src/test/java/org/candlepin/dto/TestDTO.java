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



/**
 * A simple test DTO class to use during testing
 */
public class TestDTO extends CandlepinDTO {
    protected ModelTranslator modelTranslator;
    protected ObjectTranslator objectTranslator;
    protected Object source;

    public TestDTO() {
        // Intentionally left empty
    }

    public TestDTO(ModelTranslator modelTranslator, ObjectTranslator objectTranslator, Object source) {
        this.setModelTranslator(modelTranslator);
        this.setObjectTranslator(objectTranslator);
        this.setSourceObject(source);
    }

    public ModelTranslator getModelTranslator() {
        return this.modelTranslator;
    }

    public TestDTO setModelTranslator(ModelTranslator modelTranslator) {
        this.modelTranslator = modelTranslator;
        return this;
    }

    public ObjectTranslator getObjectTranslator() {
        return this.objectTranslator;
    }

    public TestDTO setObjectTranslator(ObjectTranslator objectTranslator) {
        this.objectTranslator = objectTranslator;
        return this;
    }

    public Object getSourceObject() {
        return this.source;
    }

    public TestDTO setSourceObject(Object source) {
        this.source = source;
        return this;
    }

    public int hashCode() {
        return ((Object) this).hashCode();
    }

    public boolean equals(Object comp) {
        return ((Object) this).equals(comp);
    }
}
