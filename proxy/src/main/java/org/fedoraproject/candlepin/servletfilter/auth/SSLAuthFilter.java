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
package org.fedoraproject.candlepin.servletfilter.auth;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.LinkedList;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.ConfigDirectory;

public class SSLAuthFilter implements Filter {
    private static final String CA_CERTIFICATE = "ca.crt";
    private static final String CERTIFICATES_ATTR = "javax.servlet.request.X509Certificate";
    
    private static Logger log = Logger.getLogger(BasicAuthViaDbFilter.class);
    
    private FilterConfig filterConfig = null;
    private CertificateFactory certificateFactory;
    private PKIXParameters pKIXparams;
    
    public SSLAuthFilter() throws CertificateException, FileNotFoundException, InvalidAlgorithmParameterException {
        certificateFactory = CertificateFactory.getInstance("X.509");
        
        X509Certificate caCertificate = (X509Certificate) certificateFactory
            .generateCertificate(caCertificate());
        
        TrustAnchor anchor = new TrustAnchor((X509Certificate) caCertificate, null);
        pKIXparams = new PKIXParameters(Collections.singleton(anchor));
        pKIXparams.setRevocationEnabled(false);
    }
    
    public void init(FilterConfig filterConfig) throws ServletException {
       this.filterConfig = filterConfig;
    }
    
    public void destroy() {
       this.filterConfig = null;
    }
    
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
           throws IOException, ServletException {
        
        debugMessage("in ssl auth filter");
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        try {
            X509Certificate certs[] = (X509Certificate[]) httpRequest.getAttribute(CERTIFICATES_ATTR);
            
            if (certs == null || certs.length < 1) {
                debugMessage("no certificate was present to authenticate the client");
                httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            
            CertPathValidator cpv = CertPathValidator.getInstance("PKIX");
            for (final X509Certificate cert: certs) {
                CertPath cp = certificateFactory.generateCertPath(
                    new LinkedList<Certificate>() {{ add(cert); }}
                );
                
                try {
                    PKIXCertPathValidatorResult result = 
                        (PKIXCertPathValidatorResult) cpv.validate(cp, pKIXparams);
    
                    debugMessage("validated cert with subject DN: " 
                        + cert.getSubjectDN() + "and issuer DN: " + cert.getIssuerDN());
                    
                    chain.doFilter(request, response);
                    
                    debugMessage("leaving ssl auth filter");
                    return;
                } catch (CertPathValidatorException e) {
                    debugMessage("validation exception for a cert with subject DN: " 
                            + cert.getSubjectDN() + "and issuer DN: " + cert.getIssuerDN());
                }
            }
            
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            debugMessage("leaving ssl auth filter; 403 returned");
            
        } catch (Exception e) {
            log.error(e.getMessage());
            httpResponse.setStatus(HttpServletResponse.SC_BAD_GATEWAY); // TODO: not sure about 503 return code.
        } 
    }

    private void debugMessage(String msg) {
        if (log.isDebugEnabled()) {
            log.debug(msg);
        }
    }
    
    protected InputStream caCertificate() throws FileNotFoundException {
        return new BufferedInputStream(
            new FileInputStream(
                new File(ConfigDirectory.directory(), CA_CERTIFICATE)
            )
        ); 
    }
}
