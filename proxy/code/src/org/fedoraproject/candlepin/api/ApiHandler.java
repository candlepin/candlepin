package org.fedoraproject.candlepin.api;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.model.BaseModel;
import org.fedoraproject.candlepin.model.ObjectFactory;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.User;

import java.util.HashSet;
import java.util.Set;

public class ApiHandler {
    
    private static ApiHandler instance = new ApiHandler();
    private Set authTokens;
    
    /**
     * Logger for this class
     */
    private static final Logger logger = Logger.getLogger(ApiHandler.class);

    private ApiHandler() {
        authTokens = new HashSet();
    }
    
    public static ApiHandler get() {
        return instance;
    }
    
    /**
     * Auth
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
    
    /** Fetch an owner
     * 
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
