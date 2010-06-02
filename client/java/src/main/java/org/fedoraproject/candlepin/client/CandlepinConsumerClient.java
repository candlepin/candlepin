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

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.net.URL;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.protocol.Protocol;
import org.fedoraproject.candlepin.client.cmds.Utils;
import org.fedoraproject.candlepin.client.model.Consumer;
import org.fedoraproject.candlepin.client.model.Entitlement;
import org.fedoraproject.candlepin.client.model.EntitlementCertificate;
import org.fedoraproject.candlepin.client.model.Pool;
import org.fedoraproject.candlepin.client.model.ProductCertificate;
import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.client.ProxyFactory;
import org.jboss.resteasy.client.core.executors.ApacheHttpClientExecutor;
import org.jboss.resteasy.plugins.providers.RegisterBuiltin;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

/**
 * CandlepinConsumerClient
 */
public class CandlepinConsumerClient {

    private Configuration config;
    public CandlepinConsumerClient(Configuration config) {
        this.config = config;
        RegisterBuiltin.register(ResteasyProviderFactory.getInstance());
    }

    /**
     * Returns true if the client is already registered
     * 
     * @return true if registered
     */
    public boolean isRegistered() {
        return getUUID() != null;
    }

    /**
     * Returns the UUID for the consumer, or null if not registered.
     * 
     * @return the UUID of the consumer
     */
    public String getUUID() {
        String uuid = null;
        File certFile = new File(this.config.getCertificateFilePath());
        if (certFile.exists()) {
            uuid = PemUtil.extractUUID(this.config.getCertificateFilePath());
        }
        return uuid;
    }

    /**
     * Registers a consumer with a provided name and type. The credentials are
     * user for authentication.
     * 
     * @return The UUID of the new consumer.
     */
    public String register(String username, String password, String name,
        String type) {
        ICandlepinConsumerClient client = this.clientWithCredentials(username,
            password);
        Consumer cons = new Consumer();
        cons.setName(name);
        cons.setType(type);
        cons = client.register(cons);
        recordIdentity(cons);
        return cons.getUuid();
    }

    /**
     * Register to an existing consumer. The credentials are user for
     * authentication.
     * 
     * @return true if the registration succeeded
     */
    public OperationResult registerExisting(String username, String password,
        String uuid) {
        ICandlepinConsumerClient client = this.clientWithCredentials(username,
            password);
        if (isRegistered()) {
            try {
                ClientResponse<Consumer> response = client.getConsumer(uuid);
                return evaluateResponse(response);
            }
            catch (Exception e) {
                e.printStackTrace();
                return OperationResult.UNKNOWN;
            }
        }
        return OperationResult.CLIENT_NOT_REGISTERED;
    }
    
    /**Register an existing consumer based on the uuid. 
     * @param uuid - the consumer id
     * @return true if customer exists else false.
     */
    public OperationResult registerExistingCustomerWithId(String uuid) {
        try {
            HttpClient httpclient = new HttpClient();
            httpclient.getParams().setAuthenticationPreemptive(true);
            ICandlepinConsumerClient client = ProxyFactory.create(
                    ICandlepinConsumerClient.class, this.config.getServerURL(),
                    new ApacheHttpClientExecutor(httpclient));
            ClientResponse<Consumer> cr = client.getConsumer(uuid);
            return evaluateResponse(cr);
        }
        catch (Exception e) {
            e.printStackTrace();
            return OperationResult.UNKNOWN;
        }
    }

    /**
     * @param cr
     * @return
     */
    private OperationResult evaluateResponse(ClientResponse<Consumer> cr) {
        if (Response.Status.OK.equals(cr.getResponseStatus())) {
            try {
                recordIdentity(cr.getEntity());
            }
            catch (ClientException e) {
                return OperationResult.ERROR_WHILE_SAVING_CERTIFICATES;
            }
            return OperationResult.NOT_A_FAILURE;
        }
        else {
            return OperationResult.INVALID_UUID;
        }
    }

    /**
     * Remove he consumer from candlepin and all of the entitlements which the
     * conumser have subscribed to.
     * 
     * @return True if the consumr is no longer registered.
     */
    public boolean unRegister() {
        ICandlepinConsumerClient client = clientWithCert();
        boolean success = false;
        if (isRegistered()) {
            ClientResponse<Object> response = client.deleteConsumer(getUUID());
            success = (response.getResponseStatus()
                .equals(Response.Status.NO_CONTENT));
            if (success) {
                removeFiles();
            }
        }
        return success;
    }

    /**
     * List the pools which the consumer could subscribe to
     * 
     * @return the list of exception
     */
    public List<Pool> listPools() {
        ICandlepinConsumerClient client = clientWithCert();
        List<Pool> pools = client.listPools(getUUID());
        return pools;
    }

    public List<Entitlement> bindByPool(Long poolId) {
        ICandlepinConsumerClient client = clientWithCert();
        return client.bindByEntitlementID(getUUID(), poolId).getEntity();
    }
    
    public List<Entitlement> bindByProductId(String productId) {
        ICandlepinConsumerClient client = clientWithCert();
        return client.bindByProductId(getUUID(), productId).getEntity();
    }
    
    public List<Entitlement> bindByRegNumber(String regNo) {
        ICandlepinConsumerClient client = clientWithCert();
        return client.bindByRegNumber(getUUID(), regNo).getEntity();
    }
    
    public OperationResult unBindBySerialNumber(int serialNumber) {
        try {
            ICandlepinConsumerClient client = clientWithCert();
            ClientResponse<Void> response = client.unBindBySerialNumber(
                getUUID(), serialNumber);
            return response.getResponseStatus().equals(
                Response.Status.NO_CONTENT) ? OperationResult.NOT_A_FAILURE :
                    OperationResult.UNKNOWN;
        }
        catch (Exception e) {
            e.printStackTrace();
            return OperationResult.UNKNOWN;
        }

    }

    public OperationResult unBindAll() {
        try {
            ICandlepinConsumerClient client = clientWithCert();
            ClientResponse<Void> response = client.unBindAll(getUUID());
            if (response.getResponseStatus().equals(Response.Status.NO_CONTENT)) {
                return OperationResult.NOT_A_FAILURE;
            }
            else {
                return OperationResult.UNKNOWN;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            return OperationResult.UNKNOWN;
        }
    }

    public boolean updateEntitlementCertificates() {
        File entitlementDir = new File(config.getEntitlementDirPath());
        if (entitlementDir.exists() && entitlementDir.isDirectory()) {
            FileUtil.removeFiles(entitlementDir.listFiles());
        }
        FileUtil.mkdir(config.getEntitlementDirPath());
        ICandlepinConsumerClient client = clientWithCert();
        List<EntitlementCertificate> certs = client
            .getEntitlementCertificates(getUUID());
        for (EntitlementCertificate cert : certs) {
            String entCertFileName = config.getEntitlementDirPath() +
                File.separator + cert.getSerial() + "-cert.pem";
            String entKeyFileName = config.getEntitlementDirPath() +
                File.separator + cert.getSerial() + "-key.pem";
            FileUtil.dumpToFile(entCertFileName, cert.getX509CertificateAsPem());
            FileUtil.dumpToFile(entKeyFileName, cert.getKey());
        }
        return true;
    }

    public List<EntitlementCertificate> getCurrentEntitlementCertificates() {
        try {
            FileUtil.mkdir(config.getEntitlementDirPath());
            File[] entitlementDirs = new File(config.getEntitlementDirPath())
                .listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.endsWith("-cert.pem");
                    }
                });

            List<EntitlementCertificate> certs = Utils.newList();
            for (File file : entitlementDirs) {
                String filename = file.getAbsolutePath();
                String eKeyFileName = filename.replace("-cert.pem", "-key.pem");
                X509Certificate cert = PemUtil.readCert(filename);
                PrivateKey key = PemUtil.readPrivateKey(eKeyFileName);
                EntitlementCertificate entCert = new EntitlementCertificate(
                    cert, key);
                certs.add(entCert);
            }

            return certs;
        }
        catch (Exception e) {
            throw new ClientException(e);
        }
    }

    public List<ProductCertificate> getInstalledProductCertificates() {
        File file = new File(this.config.getProductDirPath());
        if (file.exists() && file.isDirectory()) {
            File[] prodCerts = file.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".pem");
                }
            });
            if (prodCerts.length == 0) {
                return Collections.emptyList();
            }
            List<EntitlementCertificate> entitlementCerts = this
                .getCurrentEntitlementCertificates();
            Map<Integer, EntitlementCertificate> map = Utils.newMap();
            for (EntitlementCertificate certificate : entitlementCerts) {
                map.put(certificate.getProductID(), certificate);
            }
            List<ProductCertificate> productCertificates = Utils.newList();
            for (File certificate : prodCerts) {
                ProductCertificate pc = new ProductCertificate(PemUtil
                    .readCert(certificate.getAbsolutePath()));
                EntitlementCertificate ec = map.get(pc.getProductID());
                pc.setEntitlementCertificate(ec != null ? ec : null);
                productCertificates.add(pc);
            }

            return productCertificates;
        }
        else {
            return Collections.emptyList();
        }
    }

    public void generatePKCS12Certificates(String password) {
        try {
            List<EntitlementCertificate> certs = getCurrentEntitlementCertificates();
            for (EntitlementCertificate cert : certs) {
                KeyStore store = PKCS12Util.createPKCS12Keystore(cert
                    .getX509Certificate(), cert.getPrivateKey(), null);
                File p12File = new File(config.getEntitlementDirPath() + File.separator +
                    cert.getSerial() + ".p12");
                store.store(new FileOutputStream(p12File), password
                    .toCharArray());
            }
        }
        catch (Exception e) {
            throw new ClientException(e);
        }
    }
    
    protected ICandlepinConsumerClient clientWithCert() {
        try {
            HttpClient httpclient = new HttpClient();
            CustomSSLProtocolSocketFactory factory  =
                new CustomSSLProtocolSocketFactory(
                    config.getCertificateFilePath(), config.getKeyFilePath(), config);
            URL hostUrl = new URL(config.getServerURL());
            Protocol customHttps = new Protocol("https", factory, 8443);
            Protocol.registerProtocol("https", customHttps);
            httpclient.getHostConfiguration().setHost(hostUrl.getHost(),
                hostUrl.getPort(), customHttps);
            httpclient.getParams().setConnectionManagerTimeout(1000);
            ICandlepinConsumerClient client = ProxyFactory.create(
                ICandlepinConsumerClient.class, config.getServerURL(),
                new ApacheHttpClientExecutor(httpclient));
            return client;
        }
        catch (Exception e) {
            throw new ClientException(e);
        }

    }

    protected ICandlepinConsumerClient clientWithCredentials(String username,
        String password) {
        HttpClient httpclient = new HttpClient();
        httpclient.getParams().setAuthenticationPreemptive(true);
        UsernamePasswordCredentials creds = new UsernamePasswordCredentials(
            username, password);
        httpclient.getState().setCredentials(AuthScope.ANY, creds);

        ICandlepinConsumerClient client = ProxyFactory.create(
            ICandlepinConsumerClient.class, config.getServerURL(),
            new ApacheHttpClientExecutor(httpclient));
        return client;
    }

    protected void recordIdentity(Consumer aConsumer) {
        FileUtil.mkdir(config.getConsumerDirPath());
        FileUtil.dumpToFile(config.getCertificateFilePath(),
            aConsumer.getIdCert().getCert());
        FileUtil.dumpToFile(config.getKeyFilePath(), aConsumer.getIdCert().getKey());
    }

    protected void removeFiles() {
        FileUtil.removeFiles(new String[]{ config.getCertificateFilePath(),
            config.getKeyFilePath() });
    }

}
