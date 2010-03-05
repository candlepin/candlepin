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
package org.fedoraproject.candlepin.jaas;

import java.io.IOException;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;

/**
 * 
 * JAAS login module for ssl-based client authentication
 *
 */
public class SSLLoginModule implements LoginModule {
    
    public final static String CLIENT_ROLE = "client";
    
    private CallbackHandler handler;
    private Subject subject;
    private String userDN;

    @Override
    public boolean abort() throws LoginException {
        return false;
    }

    @Override
    public boolean commit() throws LoginException {
        try {
            CandlepinUserPrincipal user = new CandlepinUserPrincipal(userDN);
            CandlepinRolePrincipal role = new CandlepinRolePrincipal(CLIENT_ROLE);

            subject.getPrincipals().add(user);
            subject.getPrincipals().add(role);

            return true;
        } 
        catch (Exception e) {
            throw new LoginException(e.getMessage());
        }
    }

    @Override
    public void initialize(Subject aSubject, 
            CallbackHandler aCallbackHandler, Map aSharedState, Map aOptions) {
        handler = aCallbackHandler;
        subject = aSubject;
    }

    @Override
    public boolean login() throws LoginException {
        Callback[] callbacks = new Callback[1];
        callbacks[0] = new NameCallback("login");

        try {
            handler.handle(callbacks);

            String name = ((NameCallback) callbacks[0]).getName();

            userDN = name;
            return true;
        } 
        catch (IOException e) {
            throw new LoginException(e.getMessage());
        }
        catch (UnsupportedCallbackException e) {
            throw new LoginException(e.getMessage());
        }
    }

    @Override
    public boolean logout() throws LoginException {
        try {
            CandlepinUserPrincipal user = new CandlepinUserPrincipal(userDN);
            CandlepinRolePrincipal role = new CandlepinRolePrincipal(CLIENT_ROLE);

            subject.getPrincipals().remove(user);
            subject.getPrincipals().remove(role);

            return true;
        } 
        catch (Exception e) {
            throw new LoginException(e.getMessage());
        }
    }

}
