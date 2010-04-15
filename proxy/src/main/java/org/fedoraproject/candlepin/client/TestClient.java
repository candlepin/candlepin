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
package org.fedoraproject.candlepin.client;

import java.security.Security;
import java.util.List;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Pool;

/**
 * TestClient
 */
public class TestClient {

    protected TestClient() {

    }

    public static void main(String[] args) {
        try {
            // This seems like a hack.

            System.setProperty("javax.net.ssl.trustStore",
                "/home/bkearney/tomcat6/conf/keystore");
            Security.addProvider(new BouncyCastleProvider());
            // this initialization only needs to be done once per VM
            String host = "https://localhost:8443/candlepin";
            CandlepinConsumerClient client = new CandlepinConsumerClient(host);
            System.out.println("Should not be registered: " + client.isRegistered());
            String uuid = client.register("bk", "password", "Fred2", "system");
            System.out.println("UUID returned from Register: " + uuid);
            System.out.println("UUID from the getUUID call : " + client.getUUID());
            List<Pool> pools = client.listPools();
            System.out.println("Number of pools the consumer can choose from: " + pools.size());
            System.out.println("Should be registered: " + client.isRegistered());
            System.out.println("Register Existing should pass: " + client.registerExisting("bk", "password", uuid));
            System.out.println("Register Garbage should fail: " + client.registerExisting("bk", "password", "99"));
            List<Entitlement> ents = client.bindByPool(6l);
            System.out.println(ents.size());
            //System.out.println("Unregister should pass: " + client.unRegister());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
