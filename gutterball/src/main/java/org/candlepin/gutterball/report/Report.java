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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MultivaluedMap;

/**
 *
 * An abstract class that defines the common features of a report.
 *
 */
public abstract class Report {
    protected String key;
    protected String description;
    protected Map<String, ReportParameter> parameters;

    /**
     * @param key
     * @param description
     */
    public Report(String key, String description) {
        this.key = key;
        this.description = description;
        this.parameters = new HashMap<String, ReportParameter>();
        this.initParameters();
    }

    /**
     * @return the key
     */
    public String getKey() {
        return key;
    }

    /**
     * @param key the key to set
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    public List<ReportParameter> getParameters() {
        return new ArrayList<ReportParameter>(this.parameters.values());
    }

    public ReportResult run(MultivaluedMap<String, String> queryParameters) {
        validateParameters(queryParameters);
        return execute(queryParameters);
    }

    /**
     * Validates the passed parameters before the report is run.
     *
     * @param params the query parameters that were passed from the rest api call.
     */
    protected abstract void validateParameters(MultivaluedMap<String, String> params);

    /**
     * Runs this report with the provided query parameters. All parameters will
     * have already been validated.
     *
     * @param queryParameters
     * @return a {@link ReportResult} containing the results of the query.
     */
    protected abstract ReportResult execute(MultivaluedMap<String, String> queryParameters);
    /**
     * Defines the {@link ReportParameter}s that are used by this report. These
     * parameters are purely informational.
     */
    protected abstract void initParameters();

    protected void addParameter(String name, String desc, boolean mandatory,
            boolean multiValued) {
        ReportParameter param = new ReportParameter(name, desc, mandatory, multiValued);
        this.parameters.put(param.getName(), param);
    }
}
