/*
 * Copyright (c) 2014 Wael Chatila / Icegreen Technologies. All Rights Reserved.
 * This software is released under the Apache license 2.0
 * This file has been modified by the copyright holder.
 * Original file can be found at http://james.apache.org
 */
package com.icegreen.greenmail.imap;

import com.icegreen.greenmail.store.FolderException;
import com.icegreen.greenmail.store.MailFolder;
import com.icegreen.greenmail.store.MessageFlags;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.user.UserManager;

import javax.mail.Flags;
import java.util.List;

/**
 * @author Darrell DeBoer <darrell@apache.org>
 * @version $Revision: 109034 $
 */
public final class ImapSessionImpl implements ImapSession {
    private ImapSessionState state = ImapSessionState.NON_AUTHENTICATED;
    private GreenMailUser user = null;
    private ImapSessionFolder selectedMailbox = null;

    private String clientAddress;

    // TODO these shouldn't be in here - they can be provided directly to command components.
    private ImapHandler handler;
    private ImapHostManager imapHost;
    private UserManager users;

    public ImapSessionImpl(ImapHostManager imapHost,
                           UserManager users,
                           ImapHandler handler,
                           String clientAddress) {
        this.imapHost = imapHost;
        this.users = users;
        this.handler = handler;
        this.clientAddress = clientAddress;
    }

    public ImapHostManager getHost() {
        return imapHost;
    }

    public void unsolicitedResponses(ImapResponse request) throws FolderException {
        unsolicitedResponses(request, false);
    }

    public void unsolicitedResponses(ImapResponse response, boolean omitExpunged) throws FolderException {
        ImapSessionFolder selected = getSelected();
        if (selected != null) {
            // New message response
            if (selected.isSizeChanged()) {
                response.existsResponse(selected.getMessageCount());
                response.recentResponse(selected.getRecentCount(true));
                selected.setSizeChanged(false);
            }

            // Message updates
            final List<ImapSessionFolder.FlagUpdate> flagUpdates = selected.getFlagUpdates();
            for (ImapSessionFolder.FlagUpdate update : flagUpdates) {
                int msn = update.getMsn();
                Flags updatedFlags = update.getFlags();
                StringBuilder out = new StringBuilder("FLAGS ");
                out.append(MessageFlags.format(updatedFlags));
                if (update.getUid() != null) {
                    out.append(" UID ");
                    out.append(update.getUid());
                }
                response.fetchResponse(msn, out.toString());
            }

            // Expunged messages
            if (!omitExpunged) {
                int[] expunged = selected.getExpunged();
                for (int msn : expunged) {
                    response.expungeResponse(msn);
                }
            }
        }
    }

    public void closeConnection(String byeMessage) {
        handler.forceConnectionClose(byeMessage);
    }

    public void closeConnection() {
        handler.resetHandler();
    }

    public UserManager getUserManager() {
        return users;
    }

    public String getClientIP() {
        return clientAddress;
    }

    public void setAuthenticated(GreenMailUser user) {
        this.state = ImapSessionState.AUTHENTICATED;
        this.user = user;
    }

    public GreenMailUser getUser() {
        return this.user;
    }

    public void deselect() {
        this.state = ImapSessionState.AUTHENTICATED;
        if (selectedMailbox != null) {
            // TODO is there more to do here, to cleanup the mailbox.
            selectedMailbox.removeListener(selectedMailbox);
            this.selectedMailbox = null;
        }
    }

    public void setSelected(MailFolder folder, boolean readOnly) {
        ImapSessionFolder sessionMailbox = new ImapSessionFolder(folder, this, readOnly);
        this.state = ImapSessionState.SELECTED;
        this.selectedMailbox = sessionMailbox;
    }

    public ImapSessionFolder getSelected() {
        return this.selectedMailbox;
    }

    public boolean selectedIsReadOnly() {
        return selectedMailbox.isReadonly();
    }

    public ImapSessionState getState() {
        return this.state;
    }

}
