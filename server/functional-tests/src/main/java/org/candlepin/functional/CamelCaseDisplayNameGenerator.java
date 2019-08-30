/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.functional;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DisplayNameGenerator;

import java.lang.reflect.Method;

/** DisplayNameGenerator that splits camel case method names */
public class CamelCaseDisplayNameGenerator extends DisplayNameGenerator.Standard {
    @Override
    public String generateDisplayNameForMethod(Class<?> testClass, Method testMethod) {
        String camelCaseName = StringUtils.capitalize(
            StringUtils.join(StringUtils.splitByCharacterTypeCamelCase(testMethod.getName()), ' ')
        );
        String params = DisplayNameGenerator.parameterTypesAsString(testMethod);
        if ("()".equals(params)) {
            return camelCaseName;
        }
        else {
            return camelCaseName + params;
        }
    }
}
