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
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
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
        Pattern.compile(".*[V|v]ersion:.((?:(\\d+)\\.)?(?:(\\d+)\\.)?(\\*|\\d+)).*");

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    private String id;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "rules_blob")
    private String rules;

    @Column(name = "version", nullable = false, length = 20)
    private String version;

    /**
     * ctor
     * @param rulesBlob Rules script
     */
    public Rules(String rulesBlob) {
        this.rules = rulesBlob;

        this.version = "";
        // Look for a "version" in the first line of the rules file:
        String [] lines = this.rules.split("\n");
        if (lines.length == 0) {
            throw new RuleParseException("Unable to read version from rules file.");
        }
        Matcher m = VERSION_REGEX.matcher(lines[0]);
        if (m.matches()) {
            this.version = m.group(1);
        }
        else {
            throw new RuleParseException("Unable to read version from rules file.");
        }
    }

    /**
     * default ctor
     */
    public Rules() {
    }

    /**
     * @return rules blob
     */
    public String getRules() {
        return rules;
    }


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

}
