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
package org.candlepin.model;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

import org.candlepin.policy.js.RuleParseException;
import org.hibernate.annotations.GenericGenerator;

/**
 * Rules
 */
@Entity
@Table(name = "cp_rules")
@Embeddable
public class Rules extends AbstractHibernateObject {

    private static final Pattern VERSION_REGEX =
        Pattern.compile("[//|#]+ *[V|v]ersion: *([0-9]+(\\.[0-9]+)*) *");

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid2")
    @Column(length = 37)
    private String id;

    /**
     * Length of field is required by hypersonic in the unit tests only
     * This is enough to cover current rules, plus some.
     * 4194304 bytes = 4 MB
     */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "rules_blob", length = 4194304)
    private String rules;

    @Enumerated(EnumType.STRING)
    @Column(name = "rulessource")
    private RulesSourceEnum rulesSource = RulesSourceEnum.UNDEFINED;

    @Column(name = "version", nullable = false, length = 20)
    private String version;

    /**
     * RulesSource enumerates the possible sources
     * of rules.
     */
    public enum RulesSourceEnum {
        UNDEFINED("undefined"),
        DATABASE("database"),
        DEFAULT("default");

        private String label;

        RulesSourceEnum(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return this.label;
        }
    }

   /**
    * default ctor
    */
    public Rules() {
    }

    /**
     * ctor
     * @param rulesBlob Rules script
     */
    public Rules(String rulesBlob) {
        this.rules = rulesBlob;

        this.version = "";
        // Look for a "version" in the first line of the rules file:

        String versionLine = getVersionLine();
        if (versionLine.isEmpty()) {
            throw new RuleParseException("Unable to read version from rules file: " +
                " version not defined");
        }

        Matcher m = VERSION_REGEX.matcher(versionLine);
        if (!m.matches()) {
            throw new RuleParseException("Unable to read version from rules file. " +
                "Rules version must be specified on the top of the rules file. " +
                "For example: // Version: x.y");
        }
        this.version = m.group(1);

    }


    /**
     * @return the rulesSource
     */
    public RulesSourceEnum getRulesSource() {
        return rulesSource;
    }

    /**
     * @return the rulesSource String
     */
    public String getRulesSourceString() {
        return this.rulesSource.toString();
    }

    /**
     * @param rulesSourceEnum the rulesSourceEnum to set
     */
    public void setRulesSource(RulesSourceEnum rulesSourceEnum) {
        this.rulesSource = rulesSourceEnum;
    }

    /**
     * @return rules blob
     */
    public String getRules() {
        return rules;
    }

    // why is getId returning a Serializable rather than a String?
    @Override
    public Serializable getId() {
        return this.id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    private String getVersionLine() {
        int firstLineEndIdx = this.rules.indexOf("\n");
        firstLineEndIdx = firstLineEndIdx < 0 ? this.rules.length() : firstLineEndIdx;
        return this.rules.substring(0, firstLineEndIdx);
    }

}
