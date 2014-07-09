/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package net.oauth.signature;

import net.oauth.OAuthException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CustomerSigner class overrides the default signature verification
 * class so that we can enhance the debug output.
 */
public class CustomSigner extends HMAC_SHA1 {

    private static Logger log = LoggerFactory.getLogger(CustomSigner.class);

    /**
     * Does nothing but make this method public
     * @throws OAuthException with the reason why it is invalid.
     * @return the actual signature
     */
    public String getSignature(String baseString) throws OAuthException {
        String returnValue =  super.getSignature(baseString);
        return returnValue;
    }

    /**
     * Call the superclass and log out the response.
     * @throws OAuthException with the reason why it is invalid.
     * @return true if the signature if valid
     * @throws OAuthException when a signature is not valid.
     */
    public boolean isValid(String signature, String baseString)
        throws OAuthException {
        log.debug(String.format("Signature for %s is %s", baseString,
            this.getSignature(baseString)));
        return super.isValid(signature, baseString);
    }

}
