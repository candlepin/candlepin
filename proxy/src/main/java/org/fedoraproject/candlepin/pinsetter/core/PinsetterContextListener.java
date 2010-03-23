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

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * PinsetterContextListener
 * @version $Rev$
 */
public class PinsetterContextListener implements ServletContextListener {
    private PinsetterKernel pk;

    /**
     * {@inheritDoc}
     */
    @Override
    public void contextDestroyed(ServletContextEvent sceIn) {
        if (pk != null) {
            pk.shutdown();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void contextInitialized(ServletContextEvent sceIn) {
        System.out.println("ctx initialized");
        try {
            if (pk == null) {
                pk = new PinsetterKernel();
            }
            pk.startup();
        }
        catch (PinsetterException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (InstantiationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
