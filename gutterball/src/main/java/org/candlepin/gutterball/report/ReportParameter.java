/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

package org.candlepin.gutterball.report;

import javax.ws.rs.core.MultivaluedMap;

/**
 *
 * A report parameter. A parameter's descriptor describes all properties and
 * validations of this parameter.
 *
 */
public class ReportParameter {

    private ParameterDescriptor descriptor;

    /**
    * @param descriptor the {@link ParameterDescriptor} describing this parameter.
    */
    public ReportParameter(ParameterDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    /**
    * @return the name
    */
    public String getName() {
        return this.descriptor.getName();
    }

    /**
    * @return the desc
    */
    public String getDescription() {
        return this.descriptor.getDescription();
    }

    /**
    * @return the mandatory
    */
    public boolean isMandatory() {
        return this.descriptor.isMandatory();
    }

    /**
    * @return the multiValued
    */
    public boolean isMultiValued() {
        return descriptor.isMultiValued();
    }

    public void validate(MultivaluedMap<String, String> queryParams) throws ParameterValidationException {
        this.descriptor.validate(queryParams);
    }
}
