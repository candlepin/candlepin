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
package org.fedoraproject.candlepin.model.test;

import org.fedoraproject.candlepin.model.BaseModel;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ObjectFactory;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;



public class TestUtil {

    public static Owner createOwner() {
        String lookedUp = BaseModel.generateUUID();
        Owner o = new Owner();
        o.setUuid(lookedUp);
        ObjectFactory.get().store(o);
        return o;
    }

    public static Consumer createConsumer(Owner owner) {
        Consumer c = new Consumer(BaseModel.generateUUID());
        c.setOwner(owner);
        ObjectFactory.get().store(c);
        return c;
    }

    /**
     * Create a consumer with a new owner
     * @return Consumer
     */
    public static Consumer createConsumer() {
        return createConsumer(createOwner());
    }

    public static Product createProduct() {
        Product rhel = new Product(BaseModel.generateUUID());
        rhel.setName("Red Hat Enterprise Linux");
        ObjectFactory.get().store(rhel);
        return rhel;
    }
}
