/*
 * Copyright (c) 2014 Wael Chatila / Icegreen Technologies. All Rights Reserved.
 * This software is released under the Apache license 2.0
 */
package com.icegreen.greenmail.util;

import com.icegreen.greenmail.Managers;
import com.icegreen.greenmail.imap.ImapServer;
import com.icegreen.greenmail.pop3.Pop3Server;
import com.icegreen.greenmail.smtp.SmtpManager;
import com.icegreen.greenmail.smtp.SmtpServer;
import com.icegreen.greenmail.store.StoredMessage;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.user.UserException;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

/**
 * @author Wael Chatila
 * @version $Id: $
 * @since Jan 28, 2006
 */
public class GreenMail {
    Managers managers;
    HashMap<String, Service> services;

    /**
     * Creates a SMTP, SMTPS, POP3, POP3S, IMAP, and IMAPS server binding onto non-default ports.
     * The ports numbers are defined in {@link ServerSetupTest}
     */
    public GreenMail() {
        this(ServerSetupTest.ALL);
    }

    /**
     * Call this constructor if you want to run one of the email servers only
     * @param config
     */
    public GreenMail(ServerSetup config) {
        this(new ServerSetup[]{config});
    }

    /**
     * Call this constructor if you want to run more than one of the email servers
     * @param config
     */
    public GreenMail(ServerSetup[] config) {
        managers = new Managers();
        services = new HashMap<String, Service>();
        for (ServerSetup setup : config) {
            if (services.containsKey(setup.getProtocol())) {
                throw new IllegalArgumentException("Server '" + setup.getProtocol() + "' was found at least twice in the array");
            }
            final String protocol = setup.getProtocol();
            if (protocol.startsWith(ServerSetup.PROTOCOL_SMTP)) {
                services.put(protocol, new SmtpServer(setup, managers));
            } else if (protocol.startsWith(ServerSetup.PROTOCOL_POP3)) {
                services.put(protocol, new Pop3Server(setup, managers));
            } else if (protocol.startsWith(ServerSetup.PROTOCOL_IMAP)) {
                services.put(protocol, new ImapServer(setup, managers));
            }
        }
    }


    public synchronized void start() {
        for (Service service : services.values()) {
            service.startService(null);
        }
        //quick hack
        boolean allup = false;
        for (int i=0;i<200 && !allup;i++) {
            allup = true;
            for (Service service : services.values()) {
                allup = allup && service.isRunning();
            }
            if (!allup) {
                try {
                    wait(5);
                } catch (InterruptedException e) {
                    // We don't care
                }
            }
        }
        if (!allup) {
            throw new RuntimeException("Couldnt start at least one of the mail services.");
        }
    }

    public synchronized void stop() {
        for (Service service : services.values()) {
            service.stopService(null);
        }
    }

    public SmtpServer getSmtp() {
        return (SmtpServer) services.get(ServerSetup.PROTOCOL_SMTP);
    }

    public ImapServer getImap() {
        return (ImapServer) services.get(ServerSetup.PROTOCOL_IMAP);

    }

    public Pop3Server getPop3() {
        return (Pop3Server) services.get(ServerSetup.PROTOCOL_POP3);
    }

    public SmtpServer getSmtps() {
        return (SmtpServer) services.get(ServerSetup.PROTOCOL_SMTPS);
    }

    public ImapServer getImaps() {
        return (ImapServer) services.get(ServerSetup.PROTOCOL_IMAPS);

    }

    public Pop3Server getPop3s() {
        return (Pop3Server) services.get(ServerSetup.PROTOCOL_POP3S);
    }

    public Managers getManagers() {
        return managers;
    }



    //~ Convenience Methods, often needed while testing ---------------------------------------------------------------
    /**
     * Use this method if you are sending email in a different thread from the one you're testing from.
     * Block waits for an email to arrive in any mailbox for any user.
     * Implementation Detail: No polling wait implementation
     *
     * @param timeout    maximum time in ms to wait for emailCount of messages to arrive before giving up and returning false
     * @param emailCount waits for these many emails to arrive before returning
     * @return Returns false if timeout period was reached, otherwise true.
     * @throws InterruptedException
     */
    public boolean waitForIncomingEmail(long timeout, int emailCount) throws InterruptedException {
        final SmtpManager.WaitObject o = managers.getSmtpManager().createAndAddNewWaitObject(emailCount);
        if (null == o) {
            return true;
        }

        synchronized (o) {
            long t0 = System.currentTimeMillis();
            while (!o.isArrived()) {
                //this loop is necessary to insure correctness, see documentation on Object.wait()
                o.wait(timeout);
                if ((System.currentTimeMillis() - t0) > timeout) {
                    return false;
                }

            }
        }
        return true;
    }
    /**
     * Does the same thing as {@link #wait(long, int)} but with a timeout of 5000ms
     *
     * @param emailCount waits for these many emails to arrive before returning
     * @return Returns false if timeout period was reached, otherwise true.
     * @throws InterruptedException
     */
    public boolean waitForIncomingEmail(int emailCount) throws InterruptedException {
        return waitForIncomingEmail(5000l,emailCount);
    }
    /**
     * @return Returns all messags in all folders for all users
     * {@link GreenMailUtil} has a bunch of static helper methods to extract body text etc.
     */
    public MimeMessage[] getReceivedMessages() {
        List msgs = managers.getImapHostManager().getAllMessages();
        MimeMessage[] ret = new MimeMessage[msgs.size()];
        for (int i = 0; i < msgs.size(); i++) {
            StoredMessage storedMessage = (StoredMessage) msgs.get(i);
            ret[i] = storedMessage.getMimeMessage();
        }
        return ret;
    }

    /**
     * This method can be used as an easy 'catch-all' mechanism.
     * @param domain returns all receved messages arrived to domain.
     */
    public MimeMessage[] getReceviedMessagesForDomain(String domain) {
        List<StoredMessage> msgs = managers.getImapHostManager().getAllMessages();
        List<MimeMessage> ret = new ArrayList<MimeMessage>();
        try {
            for (StoredMessage msg : msgs) {
                String tos = GreenMailUtil.getAddressList(msg.getMimeMessage().getAllRecipients());
                if (tos.toLowerCase().contains(domain)) {
                    ret.add(msg.getMimeMessage());
                }
            }
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
        return ret.toArray(new MimeMessage[ret.size()]);
    }
    /**
     * Sets the password for the account linked to email. If no account exits, one is automatically created when an email is received
     * The automatically created account has the account login and password equal to the email address.
     *
     * @param email
     * @param password
     */
    public GreenMailUser setUser(String email, String password) {
        return setUser(email, email, password);
    }

    public GreenMailUser setUser(String email, String login, String password) {
        GreenMailUser user = managers.getUserManager().getUser(email);
        if (null == user) {
            try {
                user = managers.getUserManager().createUser(email, login, password);
            } catch (UserException e) {
                throw new RuntimeException(e);
            }
        } else {
            user.setPassword(password);
        }
        return user;
    }

    /**
     * Toggles the IMAP quota support.
     *
     * Quotas are enabled by default.
     *
     * @param isEnabled true, if quotas should be supported.
     */
    public void setQuotaSupported(boolean isEnabled) {
        managers.getImapHostManager().getStore().setQuotaSupported(isEnabled);
    }

    /**
     * Sets up accounts with password based on a properties map where the key is the email and the value the password
     *
     * @param users
     */
    public void setUsers(Properties users) {
        for (Object o : users.keySet()) {
            String email = (String) o;
            String password = users.getProperty(email);
            setUser(email, email, password);
        }
    }

    public GreenMailUtil util() {
        return GreenMailUtil.instance();
    }
}
