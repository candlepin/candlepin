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


/**
 * The ParameterValidator interface defines a simple API for extending parameter validation.
 */
public interface ParameterValidator {

    /**
     * Called when the specified value is to be validated. If the value is valid, this method should
     * return silently. Otherwise, a ParameterValidationException should be thrown with a
     * human-readable explanation as to why validation failed.
     *
     * @param descriptor
     *  The ParameterDescriptor for which the validation is being perform.
     *
     * @param value
     *  The value to validate.
     *
     * @throws ParameterValidationException
     *  if the value is not valid for the given descriptor.
     */
    void validate(ParameterDescriptor descriptor, String value) throws ParameterValidationException;

}
