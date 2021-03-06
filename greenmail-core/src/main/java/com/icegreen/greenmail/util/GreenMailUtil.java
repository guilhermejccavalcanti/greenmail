/*
 * Copyright (c) 2014 Wael Chatila / Icegreen Technologies. All Rights Reserved.
 * This software is released under the Apache license 2.0
 */
package com.icegreen.greenmail.util;

import com.icegreen.greenmail.user.GreenMailUser;
import com.sun.mail.imap.IMAPStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.*;
import java.util.Properties;
import java.util.Random;

/**
 * @author Wael Chatila
 * @version $Id: $
 * @since Jan 29, 2006
 */
public class GreenMailUtil {

    private static final Logger log = LoggerFactory.getLogger(GreenMailUtil.class);

    /**
     * used internally for {@link #random()}
     */
    private static int generateCount = 0;

    private static final String generateSet = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPRSTUVWXYZ23456789";

    private static final int generateSetSize = generateSet.length();

    private static GreenMailUtil instance = new GreenMailUtil();

    private GreenMailUtil() {
    // empty
    }

    public static GreenMailUtil instance() {
        return instance;
    }

    /**
     * Writes the content of an input stream to an output stream
     *
     * @throws IOException
     */
    public static void copyStream(final InputStream src, OutputStream dest) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = src.read(buffer)) > -1) {
            dest.write(buffer, 0, read);
        }
        dest.flush();
    }

    /**
     * Convenience method which creates a new {@link MimeMessage} from an input stream
     */
    public static MimeMessage newMimeMessage(InputStream inputStream) {
        try {
            return new MimeMessage(Session.getDefaultInstance(new Properties()), inputStream);
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Convenience method which creates a new {@link MimeMessage} from a string
     *
     * @throws MessagingException
     */
    public static MimeMessage newMimeMessage(String mailString) throws MessagingException {
        try {
            byte[] bytes = mailString.getBytes("US-ASCII");
            return newMimeMessage(new ByteArrayInputStream(bytes));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean hasNonTextAttachments(Part m) {
        try {
            Object content = m.getContent();
            if (content instanceof MimeMultipart) {
                MimeMultipart mm = (MimeMultipart) content;
                for (int i = 0; i < mm.getCount(); i++) {
                    BodyPart p = mm.getBodyPart(i);
                    if (hasNonTextAttachments(p)) {
                        return true;
                    }
                }
                return false;
            } else {
                return !m.getContentType().trim().toLowerCase().startsWith("text");
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return Returns the number of lines in any string
     */
    public static int getLineCount(String str) {
        LineNumberReader reader = new LineNumberReader(new StringReader(str));
        try {
            reader.skip(Long.MAX_VALUE);
            return reader.getLineNumber();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                log.warn("Can not close reader", e);
            }
        }
    }

    /**
     * @return The content of an email (or a Part)
     */
    public static String getBody(Part msg) {
        String all = getWholeMessage(msg);
        int i = all.indexOf("\r\n\r\n");
        return all.substring(i + 4, all.length());
    }

    /**
     * @return The headers of an email (or a Part)
     */
    public static String getHeaders(Part msg) {
        String all = getWholeMessage(msg);
        int i = all.indexOf("\r\n\r\n");
        return all.substring(0, i);
    }

    /**
     * @return The both header and body for an email (or a Part)
     */
    public static String getWholeMessage(Part msg) {
        try {
            ByteArrayOutputStream bodyOut = new ByteArrayOutputStream();
            msg.writeTo(bodyOut);
            return bodyOut.toString("US-ASCII").trim();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] getBodyAsBytes(Part msg) {
        return getBody(msg).getBytes();
    }

    public static byte[] getHeaderAsBytes(Part part) {
        return getHeaders(part).getBytes();
    }

    /**
     * @return same as {@link #getWholeMessage(javax.mail.Part)} }
     */
    public static String toString(Part msg) {
        return getWholeMessage(msg);
    }

    /**
     * Generates a random generated password consisting of letters and digits
     * with a length variable between 5 and 8 characters long.
     * Passwords are further optimized for displays
     * that could potentially display the characters <i>1,l,I,0,O,Q</i> in a way
     * that a human could easily mix them up.
     *
     * @return the random string.
     */
    public static String random() {
        Random r = new Random();
        int nbrOfLetters = r.nextInt(3) + 5;
        return random(nbrOfLetters);
    }

    public static String random(int nbrOfLetters) {
        Random r = new Random();
        StringBuilder ret = new StringBuilder();
        for (; /* empty */
        nbrOfLetters > 0; nbrOfLetters--) {
            int pos = (r.nextInt(generateSetSize) + (++generateCount)) % generateSetSize;
            ret.append(generateSet.charAt(pos));
        }
        return ret.toString();
    }

    public static void sendTextEmailTest(String to, String from, String subject, String msg) {
        try {
            sendTextEmail(to, from, subject, msg, ServerSetupTest.SMTP);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void sendTextEmailSecureTest(String to, String from, String subject, String msg) {
        try {
            sendTextEmail(to, from, subject, msg, ServerSetupTest.SMTPS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String getAddressList(Address[] addresses) {
        if (null == addresses) {
            return null;
        }
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < addresses.length; i++) {
            if (i > 0) {
                ret.append(", ");
            }
            ret.append(addresses[i].toString());
        }
        return ret.toString();
    }

    public static void sendTextEmail(String to, String from, String subject, String msg, final ServerSetup setup) {
        try {
            Session session = getSession(setup);
            Address[] tos = new InternetAddress[] { new InternetAddress(to) };
            Address[] froms = new InternetAddress[] { new InternetAddress(from) };
            MimeMessage mimeMessage = new MimeMessage(session);
            mimeMessage.setSubject(subject);
            mimeMessage.setFrom(froms[0]);
            mimeMessage.setText(msg);
            Transport.send(mimeMessage, tos);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    // Assumes a valid JavaMail Session has been set when MimeMessage was
    // instantiated
    public static void sendMimeMessage(MimeMessage mimeMessage) {
        try {
            Transport.send(mimeMessage);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Session getSession(final ServerSetup setup) {
        return getSession(setup, null);
    }

    /**
     * Gets a JavaMail Session for given server type such as IMAP and additional props for JavaMail.
     *
     * @param setup     the setup type, such as <code>ServerSetup.IMAP</code>
     * @param mailProps additional mail properties.
     * @return the JavaMail session.
     */
    public static Session getSession(final ServerSetup setup, Properties mailProps) {
        Properties props = new Properties();
        props.put("mail.smtps.starttls.enable", Boolean.TRUE);
        if (setup.isSecure()) {
            props.setProperty("mail.smtp.socketFactory.class", DummySSLSocketFactory.class.getName());
        }
        props.setProperty("mail.transport.protocol", setup.getProtocol());
        // Should these two props be taken from the setup info?
        props.setProperty("mail.smtps.port", String.valueOf(setup.getPort()));
        props.setProperty("mail.smtp.port", String.valueOf(setup.getPort()));
        // props.setProperty("mail.debug", "true");
        // Set local host address (makes tests much faster. If this is not set
        // java mail always looks for the address)
        props.setProperty("mail.smtp.localaddress", String.valueOf(ServerSetup.getLocalHostAddress()));
        props.setProperty("mail.smtps.localaddress", String.valueOf(ServerSetup.getLocalHostAddress()));
        props.setProperty("mail." + setup.getProtocol() + ".port", String.valueOf(setup.getPort()));
        props.setProperty("mail." + setup.getProtocol() + ".host", String.valueOf(setup.getBindAddress()));
        // Otherwise, JavaMail uses a default host 'localhost'
        if (setup.isSecure() && "smtps".equals(setup.getProtocol())) {
            props.setProperty("mail.smtp.host", String.valueOf(setup.getBindAddress()));
        }
        if (null != mailProps && !mailProps.isEmpty()) {
            props.putAll(mailProps);
        }
        if (log.isDebugEnabled()) {
            log.debug("Mail session properties are " + props);
        }
        return Session.getInstance(props, null);
    }

    public static void sendAttachmentEmail(String to, String from, String subject, String msg, final byte[] attachment, final String contentType, final String filename, final String description, final ServerSetup setup) throws MessagingException, IOException {
        Session session = getSession(setup);
        Address[] tos = new InternetAddress[] { new InternetAddress(to) };
        Address[] froms = new InternetAddress[] { new InternetAddress(from) };
        MimeMessage mimeMessage = new MimeMessage(session);
        mimeMessage.setSubject(subject);
        mimeMessage.setFrom(froms[0]);
        MimeMultipart multiPart = new MimeMultipart();
        MimeBodyPart textPart = new MimeBodyPart();
        multiPart.addBodyPart(textPart);
        textPart.setText(msg);
        MimeBodyPart binaryPart = new MimeBodyPart();
        multiPart.addBodyPart(binaryPart);
        DataSource ds = new DataSource() {

            public InputStream getInputStream() throws IOException {
                return new ByteArrayInputStream(attachment);
            }

            public OutputStream getOutputStream() throws IOException {
                ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                byteStream.write(attachment);
                return byteStream;
            }

            public String getContentType() {
                return contentType;
            }

            public String getName() {
                return filename;
            }
        };
        binaryPart.setDataHandler(new DataHandler(ds));
        binaryPart.setFileName(filename);
        binaryPart.setDescription(description);
        mimeMessage.setContent(multiPart);
        Transport.send(mimeMessage, tos);
    }

    /**
     * Sets a quota for a users.
     *
     * @param user  the user.
     * @param quota the quota.
     */
    public static void setQuota(final GreenMailUser user, final Quota quota) {
        Session session = GreenMailUtil.getSession(ServerSetupTest.IMAP);
        try {
            IMAPStore store = (IMAPStore) session.getStore("imap");
            store.connect(user.getEmail(), user.getPassword());
            store.setQuota(quota);
        } catch (Exception ex) {
            throw new IllegalStateException("Can not set quota " + quota + " for user " + user, ex);
        }
    }

    /**
     * Gets the quotas for the user.
     *
     * @param user      the user.
     * @param quotaRoot the quota root, eg 'INBOX.
     * @return array of current quotas.
     */
    public static Quota[] getQuota(final GreenMailUser user, final String quotaRoot) {
        Session session = GreenMailUtil.getSession(ServerSetupTest.IMAP);
        try {
            IMAPStore store = (IMAPStore) session.getStore("imap");
            store.connect(user.getEmail(), user.getPassword());
            return store.getQuota(quotaRoot);
        } catch (Exception ex) {
            throw new IllegalStateException("Can not get quota for quota root " + quotaRoot + " for user " + user, ex);
        }
    }
}
