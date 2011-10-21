/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.model;

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
public class Rules extends AbstractHibernateObject{

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    private String id;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "rules_blob")
    private String rules;

    /**
     * ctor
     * @param rulesBlob Rules script
     */
    public Rules(String rulesBlob) {
        this.rules = rulesBlob;
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
        // TODO Auto-generated method stub
        return this.id;
    }

}
