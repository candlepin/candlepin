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

import org.apache.commons.lang.time.DateUtils;

import org.xnap.commons.i18n.I18n;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

import javax.ws.rs.core.MultivaluedMap;

/**
 * Describes all properties and validations of a {@link ReportParameter}.
 */
public class ParameterDescriptor {

    private I18n i18n;
    private String name;
    private String desc;

    // Track validation settings
    private boolean isMandatory = false;
    private boolean isMultiValued = false;
    private boolean mustBeInt = false;
    private String dateFormat = null;
    private boolean mustBeTimeZone = false;
    private List<String> mustHaveParams = new ArrayList<String>(0);
    private List<String> mustNotHaveParams = new ArrayList<String>(0);
    private List<ParameterValidator> validators = new ArrayList<ParameterValidator>(0);

    public ParameterDescriptor(I18n i18n, String name, String desc) {
        this.i18n = i18n;
        this.name = name;
        this.desc = desc;
    }

    // Property accessors
    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.desc;
    }

    public boolean isMandatory() {
        return this.isMandatory;
    }

    public boolean isMultiValued() {
        return this.isMultiValued;
    }

    // Validations

    /**
     * Sets the parameters that should not be used with this parameter.
     * @param mustNots an array of parameter names that should not be used with this
     *                 descriptor's parameter.
     * @return a reference to this descriptor.
     */
    public ParameterDescriptor mustNotHave(String ... mustNots) {
        this.mustNotHaveParams = Arrays.asList(mustNots);
        return this;
    }

    /**
     * Sets the parameters that are required to be used with this parameter.
     * @param mustHaves an array of parameter names that are required to be used with this
     *                 descriptor's parameter.
     * @return a reference to this descriptor.
     */
    public ParameterDescriptor mustHave(String ... mustHaves) {
        this.mustHaveParams = Arrays.asList(mustHaves);
        return this;
    }

    /**
     * Validate this descriptor's parameter as an Integer.
     * @return a reference to this descriptor.
     */
    public ParameterDescriptor mustBeInteger() {
        this.mustBeInt = true;
        return this;
    }

    /**
     * Validate this descriptor's parameter as a Date that follows
     * the specified date format.
     *
     * @param dateFormat the date format the parameter value should follow.
     * @return a reference to this descriptor
     */
    public ParameterDescriptor mustBeDate(String dateFormat) {
        this.dateFormat = dateFormat;
        return this;
    }

    /**
     * Validate this desctiptor's parameter as a time zone.
     *
     * @return
     *  a reference to this descriptor.
     */
    public ParameterDescriptor mustBeTimeZone() {
        this.mustBeTimeZone = true;
        return this;
    }

    /**
     * Validates this descriptor's parameter as a mandatory parameter.
     * @return a reference to this descriptor.
     */
    public ParameterDescriptor mandatory() {
        this.isMandatory = true;
        return this;
    }

    /**
     * Describes this descriptor's parameter as a multi-valued parameter.
     * @return a reference to this descriptor.
     */
    public ParameterDescriptor multiValued() {
        this.isMultiValued = true;
        return this;
    }

    /**
     * Validate this descriptor's parameter using the specified validatiors.
     *
     * @return
     *  a reference to this descriptor.
     */
    public ParameterDescriptor mustSatisfy(ParameterValidator... validators) {
        this.validators = Arrays.asList(validators);
        return this;
    }

    /**
     * Validates this descriptor based on the passed query parameters.
     *
     * @param queryParams parameters to validate against
     * @throws ParameterValidationException when a validation fails
     */
    public void validate(MultivaluedMap<String, String> queryParams) throws ParameterValidationException {
        if (!queryParams.containsKey(name)) {
            // Check for mandatory property
            if (isMandatory) {
                throw new ParameterValidationException(name, i18n.tr("Required parameter."));
            }
            // If this parameter was not specified and is
            // not mandatory there is nothing to validate.
            return;
        }

        if (this.mustBeInt) {
            validateInteger(queryParams.get(name));
        }

        if (this.dateFormat != null && !this.dateFormat.isEmpty()) {
            validateDate(queryParams.get(name));
        }

        if (this.mustBeTimeZone) {
            validateTimeZone(queryParams.get(name));
        }

        if (this.validators.size() > 0) {
            verifyValidatorsPass(queryParams.get(name));
        }

        verifyMustHaves(queryParams);
        verifyMustNotHaves(queryParams);
    }

    public ReportParameter getParameter() {
        return new ReportParameter(this);
    }

    private void verifyMustNotHaves(MultivaluedMap<String, String> queryParams) {
        for (String paramToCheck : this.mustNotHaveParams) {
            if (queryParams.containsKey(paramToCheck)) {
                throw new ParameterValidationException(name,
                        i18n.tr("Parameter must not be used with {0}.", paramToCheck));
            }
        }
    }

    private void verifyMustHaves(MultivaluedMap<String, String> queryParams) {
        for (String paramToCheck : this.mustHaveParams) {
            if (!queryParams.containsKey(paramToCheck)) {
                throw new ParameterValidationException(name,
                        i18n.tr("Parameter must be used with {0}.", paramToCheck));
            }
        }
    }

    private void verifyValidatorsPass(List<String> values) {
        for (String value : values) {
            for (ParameterValidator validator : this.validators) {
                validator.validate(this, value);
            }
        }
    }

    private void validateInteger(List<String> values) {
        for (String val : values) {
            try {
                Integer.parseInt(val);
            }
            catch (NumberFormatException nfe) {
                throw new ParameterValidationException(name,
                        i18n.tr("Parameter must be an Integer value."));
            }
        }
    }

    private void validateDate(List<String> dateStrings) {
        String[] dateFormats = { this.dateFormat };

        for (String dateString : dateStrings) {
            try {
                DateUtils.parseDateStrictly(dateString, dateFormats);
            }
            catch (ParseException pe) {
                throw new ParameterValidationException(name,
                        i18n.tr("Invalid date string. Expected format: {0}", dateFormat));
            }
        }
    }

    @SuppressWarnings("checkstyle:indentation")
    private void validateTimeZone(List<String> timezones) {
        valueloop: for (String timezone : timezones) {
            for (String tzid : TimeZone.getAvailableIDs()) {
                if (tzid.equalsIgnoreCase(timezone)) {
                    continue valueloop; // Valid time zone
                }
            }

            TimeZone result = TimeZone.getTimeZone(timezone.toUpperCase());

            String tzid = result.getID();
            if (tzid.equals("GMT") && !timezone.equals(tzid)) {
                throw new ParameterValidationException(
                    this.name,
                    i18n.tr(
                        "Invalid time zone string. Time zones must be recognized time zone names " +
                        "or offsets specified in the form of \"GMT[+-]HH:?MM\"."
                    )
                );
            }
        }
    }

}
