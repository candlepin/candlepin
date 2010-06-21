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
package org.fedoraproject.candlepin.client;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;

/**
 * AbstractSLLProtocolSocketFactory
 */
public abstract class AbstractSLLProtocolSocketFactory implements ProtocolSocketFactory {

    public Socket createSocket(String host, int port, InetAddress clientHost,
        int localPort) throws IOException, UnknownHostException {

        return getSSLContext().getSocketFactory().createSocket(host, port,
            clientHost, localPort);
    }

    public Socket createSocket(final String host, final int port,
        final InetAddress localAddress, final int localPort,
        final HttpConnectionParams params) throws IOException,
        UnknownHostException, ConnectTimeoutException {

        if (params == null) {
            throw new IllegalArgumentException("Parameters may not be null");
        }
        int timeout = params.getConnectionTimeout();
        SocketFactory socketfactory = getSSLContext().getSocketFactory();
        if (timeout == 0) {
            return socketfactory.createSocket(host, port, localAddress,
                localPort);
        }
        else {
            Socket socket = socketfactory.createSocket();
            SocketAddress localaddr = new InetSocketAddress(localAddress,
                localPort);
            SocketAddress remoteaddr = new InetSocketAddress(host, port);
            socket.bind(localaddr);
            socket.connect(remoteaddr, timeout);
            return socket;
        }
    }

    public Socket createSocket(String host, int port) throws IOException,
        UnknownHostException {

        return getSSLContext().getSocketFactory().createSocket(host, port);
    }

    /**
     * @return
     */
    protected abstract SSLContext getSSLContext();
}
