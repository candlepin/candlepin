package org.fedoraproject.candlepin.api;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import org.fedoraproject.candlepin.model.BaseModel;
import org.fedoraproject.candlepin.model.ObjectFactory;
import org.fedoraproject.candlepin.model.Organization;
import org.fedoraproject.candlepin.model.User;

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
	
	/** Organizations */
	
	/** Fetch an org 
	 * 
	 */
	public Organization getOrg(String authToken, String uuid) {
		checkToken(authToken);
		logger.debug("getOrg(String) - start: " + uuid);
		Organization retval = (Organization) ObjectFactory.get()
				.lookupByUUID(Organization.class, uuid);
		if (logger.isDebugEnabled()) {
			logger.debug("getOrg(String) - end.  returning: " + retval);
		}
		return retval;
	}
	
}
