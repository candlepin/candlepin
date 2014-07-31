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
 *
 * Report parameter metadata.
 *
 */
public class ReportParameter {
    private String name;
    private String desc;
    private boolean mandatory;
    private boolean multiValued;

    /**
    * @param name
    * @param desc
    * @param mandatory
    * @param multiValued
    */
    public ReportParameter(String name, String desc, boolean mandatory,
        boolean multiValued) {
        this.name = name;
        this.desc = desc;
        this.mandatory = mandatory;
        this.multiValued = multiValued;
    }

    /**
    * @return the name
    */
    public String getName() {
        return name;
    }

    /**
    * @param name the name to set
    */
    public void setName(String name) {
        this.name = name;
    }

    /**
    * @return the desc
    */
    public String getDesc() {
        return desc;
    }

    /**
    * @param desc the desc to set
    */
    public void setDesc(String desc) {
        this.desc = desc;
    }

    /**
    * @return the mandatory
    */
    public boolean isMandatory() {
        return mandatory;
    }

    /**
    * @param mandatory the mandatory to set
    */
    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }

    /**
    * @return the multiValued
    */
    public boolean isMultiValued() {
        return multiValued;
    }

    /**
    * @param multiValued the multiValued to set
    */
    public void setMultiValued(boolean multiValued) {
        this.multiValued = multiValued;
    }

}
