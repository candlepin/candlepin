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
package org.fedoraproject.candlepin.service.impl.stub;

import java.math.BigInteger;
import java.util.Random;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.IdentityCertificate;
import org.fedoraproject.candlepin.service.IdentityCertServiceAdapter;

public class StubIdentityCertServiceAdapter implements IdentityCertServiceAdapter {

    private Random random = new Random();
    
    @Override
    public IdentityCertificate generateIdentityCert(Consumer consumer,
            String username) {
        IdentityCertificate idCert = new IdentityCertificate();

        // totally arbitrary
        idCert.setId(43L);
        idCert.setKey("uh0876puhapodifbvj094".getBytes());
        idCert.setCert("hpj-08ha-w4gpoknpon*)&^%#".getBytes());
        idCert.setSerial(BigInteger.valueOf(random.nextInt(1000000)));

        // TODO: Should probably be saved to the database even if it is a stub...
        return idCert;
    }

    /* (non-Javadoc)
     * @see org.fedoraproject.candlepin.service.IdentityCertServiceAdapter#
     * deleteIdentityCert(org.fedoraproject.candlepin.model.Consumer)
     */
    @Override
    public void deleteIdentityCert(Consumer consumer) {
        //No Op.  Pretend to delete the cert
    }

}

