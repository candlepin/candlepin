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
package org.candlepin.swagger;

import com.fasterxml.jackson.databind.JavaType;

import java.lang.reflect.Type;

/**
 * This class is used for inheritance hack in CandlepinSwaggerModelConverter.
 * During introspection, swagger is passing Type implementation around and uses the
 * Type object to cache Entities that it has introspected. For after successfull
 * introspection of Owner entity, it will cache this introspected entity. This
 * caching is not appropriate for Candlepin when Hateoas serialization concept takes place.
 * Because with Hateoas serialization, we effectively have 2 different models for Owner:
 *   - Standard Owner serialized normally
 *   - NestedOwner serialized with only HateoasInclude fields
 *
 * Because of this fact, it is necessary to wrap nested Owner instances in this NestedComplexType
 * so that the Swagger caching understands this wrapper for nested Owner instance as a
 * separate model.
 *
 * @author fnguyen
 *
 */
public class NestedComplexType implements Type {
    private JavaType originalRawType;

    public NestedComplexType(JavaType innerType) {
        this.originalRawType = innerType;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((originalRawType == null) ? 0 : originalRawType.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        NestedComplexType other = (NestedComplexType) obj;
        if (originalRawType == null) {
            if (other.originalRawType != null) {
                return false;
            }
        }
        else if (!originalRawType.equals(other.originalRawType)) {
            return false;
        }
        return true;
    }

    public Type getOriginalType() {
        return originalRawType;
    }
}
