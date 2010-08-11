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


import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

/**
 * ExporterMetadata
 */
@Entity
@Table(name = "cp_export_metadata")
@SequenceGenerator(name = "seq_exp_meta", sequenceName = "seq_exp_meta", allocationSize = 1)
public class ExporterMetadata extends AbstractHibernateObject {

    public static final String TYPE_METADATA = "metadata";
    public static final String TYPE_CONSUMER = "consumer";
    public static final String TYPE_CONSUMER_TYPE = "consumer_type";
    public static final String TYPE_ENTITLEMENT = "entitlement";
    public static final String TYPE_ENTITLEMENT_CERT = "entitlement_cert";
    public static final String TYPE_PRODUCT = "product";
    public static final String TYPE_PRODUCT_CERT = "product_cert";
    public static final String TYPE_RULES = "rules";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_exp_meta")
    private Long id;
    @Column(nullable = false)
    private String type;
    @Column(nullable = false)
    private Date exported;

    public ExporterMetadata() {
        id = null;
        type = TYPE_METADATA;
        exported = new Date();
    }

    public ExporterMetadata(Long id, String type, Date exported) {
        this.id = id;
        this.type = type;
        this.exported = exported;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long anId) {
        id = anId;
    }

    /**
     * @param type the type to set
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * @return the exported
     */
    public Date getExported() {
        return exported;
    }

    /**
     * @param exported the exported to set
     */
    public void setExported(Date exported) {
        this.exported = exported;
    }
}
