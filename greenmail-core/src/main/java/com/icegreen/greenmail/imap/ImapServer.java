/*
 * Copyright (c) 2014 Wael Chatila / Icegreen Technologies. All Rights Reserved.
 * This software is released under the Apache license 2.0
 * This file has been modified by the copyright holder.
 * Original file can be found at http://james.apache.org
 */
package com.icegreen.greenmail.imap;

import com.icegreen.greenmail.AbstractServer;
import com.icegreen.greenmail.Managers;
import com.icegreen.greenmail.util.ServerSetup;

import java.io.IOException;
import java.net.Socket;
import java.util.Vector;

public final class ImapServer extends AbstractServer {
    protected final Vector<ImapHandler> handlers = new Vector<ImapHandler>();

    public ImapServer(ServerSetup setup, Managers managers) {
        super(setup, managers);
    }


    public synchronized void quit() {
        try {
            synchronized (handlers) {
                for (ImapHandler handler : handlers) {
                    handler.resetHandler();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try {
            if (null != serverSocket && !serverSocket.isClosed()) {
                serverSocket.close();
                serverSocket = null;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void run() {
        try {

            try {
                serverSocket = openServerSocket();
                setRunning(true);
                synchronized (this) {
                    this.notifyAll();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            while (keepOn()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    synchronized (handlers) {
                        if(!keepOn()) {
                            clientSocket.close();
                        } else {
                            ImapHandler imapHandler = new ImapHandler(managers.getUserManager(), managers.getImapHostManager(), clientSocket);
                            handlers.add(imapHandler);
                            imapHandler.start();
                        }
                    }
                } catch (IOException ignored) {
                    //ignored
                }
            }
        } finally {
            quit();
        }
    }
}