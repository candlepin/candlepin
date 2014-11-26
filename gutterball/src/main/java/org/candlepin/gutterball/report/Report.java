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

import org.candlepin.gutterball.guice.I18nProvider;

import org.xnap.commons.i18n.I18n;

import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.ws.rs.core.MultivaluedMap;

/**
 *
 * An abstract class that defines the common features of a report.
 *
 * @param <R> result object returned when a report is run.
 *
 */
public abstract class Report<R extends ReportResult> {

    protected static final String REPORT_DATE_FORMAT = "yyyy-MM-dd";
    protected static final String REPORT_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    protected I18n i18n;
    protected String key;
    protected String description;
    protected List<ReportParameter> parameters;

    /**
     * @param key
     * @param description
     */
    public Report(I18nProvider i18nProvider, String key, String description) {
        this.i18n = i18nProvider.get();
        this.key = key;
        this.description = description;
        this.parameters = new ArrayList<ReportParameter>();
        this.initParameters();
    }

    /**
     * @return the key
     */
    public String getKey() {
        return key;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    public List<ReportParameter> getParameters() {
        return this.parameters;
    }

    public R run(MultivaluedMap<String, String> queryParameters) {
        validateParameters(queryParameters);
        return execute(queryParameters);
    }

    /**
     * Validates the passed parameters before the report is run.
     *
     * @param params the query parameters that were passed from the rest api call.
     */
    protected void validateParameters(MultivaluedMap<String, String> params)
        throws ParameterValidationException {
        for (ReportParameter reportParam : this.getParameters()) {
            reportParam.validate(params);
        }
    }

    /**
     * Runs this report with the provided query parameters. All parameters will
     * have already been validated.
     *
     * @param queryParameters
     * @return a {@link ReportResult} containing the results of the query.
     */
    protected abstract R execute(MultivaluedMap<String, String> queryParameters);

    /**
     * Defines the {@link ReportParameter}s that are used by this report. These
     * parameters are purely informational.
     */
    protected abstract void initParameters();

    protected void addParameter(ReportParameter param) {
        this.parameters.add(param);
    }

    /**
     * Parses the time zone from the input string. If the time zone could not be parsed, this method
     * throws an exception.
     *
     * @param timezone
     *  The time zone string to parse.
     *
     * @throws RuntimeException
     *  if the given time zone does not represent a valid time zone.
     *
     * @return
     *  A TimeZone instance representing the specified time zone, or null if timezone is null or
     *  empty.
     */
    protected TimeZone parseTimeZone(String timezone) {
        if (timezone == null || timezone.isEmpty()) {
            return null;
        }

        // Java's time zones are case-sensitive, which is incredibly inconvenient; so we'll do
        // something rather clumsy to work around it.
        boolean found = false;
        for (String tzid : TimeZone.getAvailableIDs()) {
            if (tzid.equalsIgnoreCase(timezone)) {
                timezone = tzid;
                found = true;
                break;
            }
        }

        // If we didn't find the TZ in the available IDs, it might be a custom offset (GMT-10:00)
        // We'll convert it to upper case, since it won't match otherwise.
        if (!found) {
            timezone = timezone.toUpperCase();
        }

        TimeZone result = TimeZone.getTimeZone(timezone);

        // If we got GMT back and that's not what we requested, then it's a time zone we don't
        // support.
        String tzid = result.getID();
        if (tzid.equals("GMT") && !timezone.equals(tzid)) {
            throw new RuntimeException("Unable to parse time zone parameter");
        }

        return result;
    }

    protected Date parseDate(String date) {
        return this.parseFormattedDate(date, REPORT_DATE_FORMAT, null);
    }

    protected Date parseDateTime(String date) {
        return this.parseFormattedDate(date, REPORT_DATETIME_FORMAT, null);
    }

    protected Date parseDate(String date, TimeZone timezone) {
        return this.parseFormattedDate(date, REPORT_DATE_FORMAT, timezone);
    }

    protected Date parseDateTime(String date, TimeZone timezone) {
        return this.parseFormattedDate(date, REPORT_DATETIME_FORMAT, timezone);
    }

    protected Date parseFormattedDate(String date, String format, TimeZone timezone) {
        if (date == null || date.isEmpty()) {
            return null;
        }

        try {
            SimpleDateFormat formatter = new SimpleDateFormat(format);
            ParsePosition pos = new ParsePosition(0);

            formatter.setLenient(false);
            if (timezone != null) {
                formatter.setTimeZone(timezone);
            }

            Date result = formatter.parse(date, pos);

            if (pos.getIndex() != date.length()) {
                throw new ParseException("Could not parse date parameter", pos.getIndex());
            }

            return result;
        }
        catch (ParseException e) {
            throw new RuntimeException("Could not parse date parameter");
        }
    }
}
