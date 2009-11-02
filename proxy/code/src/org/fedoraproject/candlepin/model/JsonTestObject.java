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

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;


/**
 * JsonTestObject
 * @version $Rev$
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
public class JsonTestObject extends BaseModel {

    private List<String> stringlist = new ArrayList<String>();
    //private String[] stringarray = new String[1];
    private JsonTestObject parent;

    /**
     * sets the parent
     * @param p Parent
     */
    public void setParent(JsonTestObject p) {
        parent = p;
    }
   
    /**
     * returns parent object
     * @return parent object
     */
    public JsonTestObject getParent() {
        return parent;
    }
   
    /**
     * sets the list string
     * @param items items to set
     */
    public void setStringList(List<String> items) {
        stringlist = items;
    }

    /**
     * returns the string list
     * @return the string list
     */
    public List<String> getStringList() {
        return stringlist;
    }
}
