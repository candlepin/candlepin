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
package org.fedoraproject.candlepin.pinsetter.core;

import com.google.inject.Inject;

/**
 * PinsetterContextListener
 */
public class PinsetterContextListener {
    private PinsetterKernel kernel;

    @Inject
    public PinsetterContextListener(PinsetterKernel kernel) {
        this.kernel = kernel;
    }

    public void contextDestroyed() {
        try {
            System.out.println("shudtown");
            kernel.shutdown();
            System.out.println("after");

        }
        catch (PinsetterException e) {
            System.out.println("hello");
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void contextInitialized() {

        try {
            kernel.startup();
        }
        catch (PinsetterException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
