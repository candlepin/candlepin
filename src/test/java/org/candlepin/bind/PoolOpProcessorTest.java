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
package org.candlepin.bind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.candlepin.audit.EventSink;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;



@ExtendWith(MockitoExtension.class)
public class PoolOpProcessorTest {

    @Mock
    private PoolCurator poolCurator;
    @Mock
    private EventSink sink;
    private PoolOpProcessor processor;

    @BeforeEach
    void setUp() {
        this.processor = new PoolOpProcessor(poolCurator, sink);
    }

    @Test
    public void noPoolsToCreate() {
        PoolOperations operations = new PoolOperations();

        processor.process(operations);

        verifyNoInteractions(poolCurator);
        verifyNoInteractions(sink);
    }

    @Test
    public void testCreatePools() {
        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct();
        PoolOperations operations = new PoolOperations();
        for (int i = 0; i < 5; i++) {
            operations.createPool(TestUtil.createPool(owner, product));
        }

        processor.process(operations);

        verify(poolCurator).saveOrUpdateAll(operations.creations(), false, false);
        verify(sink, times(5)).emitPoolCreated(any(Pool.class));
    }

    @Test
    public void testUpdateQuantities() {
        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct();
        PoolOperations operations = new PoolOperations();
        for (int i = 0; i < 5; i++) {
            Pool pool = TestUtil.createPool(owner, product);
            operations.updateQuantity(pool, 5);
        }

        processor.process(operations);

        assertThat(operations.updates().keySet())
            .map(Pool::getQuantity)
            .allMatch(quantity -> quantity == 5);
        verify(poolCurator).mergeAll(anyCollection(), anyBoolean());
    }

}
