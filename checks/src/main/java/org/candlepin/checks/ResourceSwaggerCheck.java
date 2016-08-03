/**
 * Copyright (c) 2009 - 2016 Red Hat, Inc.
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
package org.candlepin.checks;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;

/**
 * Checkstyle custom check that looks for public methods in REST resource
 * classes which do not have swagger annotation.
 */
public class ResourceSwaggerCheck extends AbstractCheck {

    private final String resourceClassAnnotationName = "Path";
    private final String resourceMethodAnnotationName = "ApiOperation";
    private final String ignoreAnnotationName = "SuppressSwaggerCheck";
    private final String restPutAnnotationName = "PUT";
    private final String restGetAnnotationName = "GET";
    private int insideClass = 0;
    private boolean insideIgnoredClass = false;
    private boolean insideResourceClass = false;
    private boolean insideMethod = false;
    private boolean insideResourceMethod = false;
    private boolean foundSwaggerMethodAnnotation = false;

    @Override
    public int[] getDefaultTokens() {
        return new int[] { TokenTypes.CLASS_DEF, TokenTypes.METHOD_DEF,
            TokenTypes.IDENT };
    }

    @Override
    public void visitToken(DetailAST ast) {
        int type = ast.getType();
        if (type == TokenTypes.CLASS_DEF) {
            insideClass += 1;
        }
        else if (type == TokenTypes.METHOD_DEF && insideResourceClass &&
            ast.branchContains(TokenTypes.LITERAL_PUBLIC)) {
            insideMethod = true;
        }
        else if (type == TokenTypes.IDENT) {
            if (insideMethod &&
                ast.getText().equals(resourceMethodAnnotationName)) {
                foundSwaggerMethodAnnotation = true;
            }
            else if (insideClass > 0) {
                if (ast.getText().equals(resourceClassAnnotationName)) {
                    insideResourceClass = true;
                }
                else if (ast.getText().equals(ignoreAnnotationName)) {
                    insideIgnoredClass = true;
                }
                else if (insideResourceClass &&
                    (ast.getText().equals(restGetAnnotationName) ||
                        ast.getText().equals(restPutAnnotationName))) {
                    insideResourceMethod = true;
                }
            }
        }
    }

    @Override
    public void leaveToken(DetailAST ast) {
        int type = ast.getType();
        if (type == TokenTypes.CLASS_DEF) {
            insideClass -= 1;
            insideResourceClass = false;
            // TODO Does not handle nested ignored classes.
            insideIgnoredClass = false;
        }
        else if (type == TokenTypes.METHOD_DEF && insideResourceMethod) {
            if (!foundSwaggerMethodAnnotation && !insideIgnoredClass &&
                insideResourceMethod) {
                log(ast, "Method is missing swagger annotation.");
            }
            insideMethod = false;
            insideResourceMethod = false;
            foundSwaggerMethodAnnotation = false;
        }
    }
}
