/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.test;

import org.candlepin.model.ResultIterator;

import java.util.Iterator;



/**
 * The MockResultIterator wraps a standard iterator and provides a no-op implementation of the
 * close method to provide compatibility with the ResultIterator interface. This is intended to be
 * used to mock a proper ResultIterator during testing, where the output will be coming from a
 * static result.
 */
public class MockResultIterator<T> implements ResultIterator<T> {

    private final Iterator<T> iterator;

    public MockResultIterator(Iterator<T> iterator) {
        if (iterator == null) {
            throw new IllegalArgumentException("iterator is null");
        }

        this.iterator = iterator;
    }

    @Override
    public boolean hasNext() {
        return this.iterator.hasNext();
    }

    @Override
    public T next() {
        return this.iterator.next();
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
        // Intentionally left empty
    }
}
