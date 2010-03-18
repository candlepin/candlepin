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

import java.util.Collection;
import java.util.LinkedList;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * ClientCertificateSerials: Represents a collection of certificate serial numbers.
 * 
 * Class only exists because it doesn't seem possible to just serialize a list 
 * of strings to JSON.
 */
@XmlRootElement(name = "serials")
@XmlAccessorType(XmlAccessType.NONE)
public class CertificateSerialCollection {

    @XmlElement(name="serial")
    private Collection<Integer> serials;

    public CertificateSerialCollection() {
        this.serials = new LinkedList<Integer>();
    }

    public Collection<Integer> getSerials() {
        return serials;
    }

    public void setSerials(Collection<Integer> serials) {
        this.serials = serials;
    }

    public void addSerial(Integer serial) {
        this.serials.add(serial);
    }
}
