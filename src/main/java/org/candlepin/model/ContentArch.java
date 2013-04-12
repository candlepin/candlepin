/**
 * Copyright (c) 2009 - 2013 Red Hat, Inc.
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

import javax.persistence.Embeddable;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.xml.bind.annotation.XmlTransient;

import org.hibernate.annotations.Parent;

/**
 * ContentArch
 */
@Embeddable
public class ContentArch extends AbstractHibernateObject {

    @Parent
    private Content content;

    @ManyToOne
    @JoinColumn(name = "arch_id", nullable = false, updatable = false)
    // do we need an index here? should be a tiny table, but oft referenced
    private Arch arch;

    public ContentArch() {

    }

    public ContentArch(Content content, Arch arch) {
        this.setContent(content);
        this.setArch(arch);
    }


    /** {@inheritDoc} */
    @XmlTransient
    public Serializable getId() {
        return null;

    }

    public void setId(String s) {
        // TODO: just here to appease jackson
        return;
    }

    /**
     * @param arch
     */
    private void setArch(Arch arch) {
        this.arch = arch;
    }

    /**
     * @param content
     */
    private void setContent(Content content) {
        this.content = content;
    }

}
