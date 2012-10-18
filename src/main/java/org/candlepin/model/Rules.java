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

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;

import org.hibernate.annotations.GenericGenerator;

/**
 * Rules
 */
@Entity
@Table(name = "cp_rules")
@Embeddable
public class Rules extends AbstractHibernateObject {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    private String id;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "rules_blob")
    private String rules;

    @Column(name = "candlepin_version", nullable = false, length = 255)
    private String candlepinVersion;

    /**
     * ctor
     * @param rulesBlob Rules script
     * @param candlepinVersion Version of Candlepin these rules are from.
     */
    public Rules(String rulesBlob, String candlepinVersion) {
        this.rules = rulesBlob;
        this.candlepinVersion = candlepinVersion;
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

    /**
     * @return Version of Candlepin which exported these rules. If the rules were
     * manually uploaded by an admin, the version will be set to the current version
     * of that Candlepin server.
     */
    public String getCandlepinVersion() {
        return candlepinVersion;
    }

    public void setCandlepinVersion(String candlepinVersion) {
        this.candlepinVersion = candlepinVersion;
    }

}
