/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
 * Check taken from <a href="https://github.com/sevntu-checkstyle/sevntu.checkstyle>sevntu-checkstyle</a>
 * and licensed under the LGPL v2.1.
 *
 * This Check highlights variable definition statements where <a href=
 * "https://www.javaworld.com/article/2074080/core-java/jdk-7--the-diamond-operator.html">
 * diamond operator</a> could be used.<br>
 * <b>Rationale</b>: using diamond operator (introduced in Java 1.7) leads to shorter code<br>
 * and better code readability. It is suggested by Oracle that the diamond primarily using<br>
 * for variable declarations.<br><br>
 * E.g. of statements:
 *
 * <p><b>Without diamond operator:</b><br><code>
 * Map&lt;String, Map&lt;String, Integer&gt;&gt; someMap =
 *     new HashMap&lt;String, Map&lt;String, Integer&gt;&gt;();</code><br>
 * <b>With diamond operator:</b><br>
 * <code>
 * Map&lt;String, Map&lt;String, Integer&gt;&gt; someMap = new HashMap&lt;&gt;();
 * </code>
 * </p>
 *
 * @author <a href="mailto:nesterenko-aleksey@list.ru">Aleksey Nesterenko</a>
 */
public class DiamondOperatorForVariableDefinitionCheck extends AbstractCheck {

    /** If we had i18n capability, we would use a message key, but we don't need it for just the team - al */
    public static final String MSG_KEY = "Use the diamond operator \"<>\" instead of explict types.";

    @Override
    public int[] getDefaultTokens() {
        return new int[] {TokenTypes.VARIABLE_DEF};
    }

    @Override
    public int[] getAcceptableTokens() {
        return getDefaultTokens();
    }

    @Override
    public int[] getRequiredTokens() {
        return getDefaultTokens();
    }

    @Override
    public void visitToken(DetailAST variableDefNode) {
        final DetailAST assignNode = variableDefNode.findFirstToken(TokenTypes.ASSIGN);

        if (assignNode != null) {
            final DetailAST newNode = assignNode.getFirstChild().getFirstChild();

            // we check only creation by NEW
            if (newNode.getType() == TokenTypes.LITERAL_NEW) {
                final DetailAST variableDefNodeType =
                    variableDefNode.findFirstToken(TokenTypes.TYPE);
                final DetailAST varDefArguments = getFirstTypeArgumentsToken(variableDefNodeType);

                // generics has to be on left side
                if (varDefArguments != null &&
                    newNode.getLastChild().getType() != TokenTypes.OBJBLOCK &&
                    // arrays can not be generics
                    newNode.findFirstToken(TokenTypes.ARRAY_DECLARATOR) == null) {
                    final DetailAST typeArgs = getFirstTypeArgumentsToken(newNode);

                    if (varDefArguments.equalsTree(typeArgs)) {
                        log(typeArgs, MSG_KEY);
                    }
                }
            }
        }
    }

    /**
     * Get first occurrence of TYPE_ARGUMENTS if exists.
     * @param rootToken the token to start search from.
     * @return TYPE_ARGUMENTS token if found.
     */
    private static DetailAST getFirstTypeArgumentsToken(DetailAST rootToken) {
        DetailAST resultNode = rootToken.getFirstChild();

        if (resultNode != null) {
            if (resultNode.getType() == TokenTypes.DOT) {
                resultNode = resultNode.getFirstChild().getNextSibling();
            }
            final DetailAST childNode = getFirstTypeArgumentsToken(resultNode);

            if (childNode == null) {
                resultNode = resultNode.getNextSibling();
            }
        }

        return resultNode;
    }
}
