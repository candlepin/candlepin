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
package org.fedoraproject.candlepin.resource;

import org.fedoraproject.candlepin.model.BaseModel;
import org.fedoraproject.candlepin.model.ObjectFactory;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.User;

import org.apache.log4j.Logger;

import java.util.HashSet;
import java.util.Set;
/**
 * API gateway for authentication
 * TODO: do we still need this?
 */
public class ApiHandler {
    
    private static ApiHandler instance = new ApiHandler();
    private Set authTokens;
    
    /**
     * Logger for this class
     */
    private static Logger logger = Logger.getLogger(ApiHandler.class);

    private ApiHandler() {
        authTokens = new HashSet();
    }
   
    /**
     * returns instance of this singleton.
     * @return instance of this singleton.
     */
    public static ApiHandler get() {
        return instance;
    }
    
    /**
     * Auth
     * @param login username
     * @param password password
     * @return token
     */
    public String login(String login, String password) {
        User u = (User) ObjectFactory.get().
            lookupByFieldName(User.class, "login", login);
        if (u == null) {
            return null;
        }
        if (u.getPassword().equals(password)) {
            String newtoken = BaseModel.generateUUID();
            authTokens.add(newtoken);
            return newtoken;
        }
        else {
            return null;
        }
    }
    
    private void checkToken(String token) throws AuthenticationException {
        if (!authTokens.contains(token)) {
            throw new AuthenticationException("token not valid: " + token);
        }
    }
    
    /** Owners */
    
    /**
     * Fetch an owner
     * @param authToken token
     * @param uuid unique id of owner
     * @return owner
     */
    public Owner getOwner(String authToken, String uuid) {
        checkToken(authToken);
        logger.debug("getOrg(String) - start: " + uuid);
        Owner retval = (Owner) ObjectFactory.get()
                .lookupByUUID(Owner.class, uuid);
        if (logger.isDebugEnabled()) {
            logger.debug("getOrg(String) - end.  returning: " + retval);
        }
        return retval;
    }
    
}
