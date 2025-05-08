package com.bw.sshTerm;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

/**
 * Example for usage of com.jcraft.jsch.UserInfo,
 */
public class UserInfo implements com.jcraft.jsch.UserInfo {

    private String passwd;
    private String user;
    private JComponent parent;

    public UserInfo(JComponent parent) {
        this.parent = parent;
    }

    @Override
    public String getPassword() {
        return passwd;
    }

    @Override
    public boolean promptPassword(String message) {
        JPasswordField passwordField = new JPasswordField();
        // Workaround for missing focus on this field.
        passwordField.addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent event) {
                passwordField.requestFocus();
            }

            @Override
            public void ancestorRemoved(AncestorEvent event) {
            }

            @Override
            public void ancestorMoved(AncestorEvent event) {
            }
        });
        int option = JOptionPane.showOptionDialog(parent, passwordField, message, JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null);
        if (option == JOptionPane.OK_OPTION) {
            passwd = new String(passwordField.getPassword());
        }
        return passwd != null && !passwd.isEmpty();
    }

    @Override
    public boolean promptYesNo(String message) {
        int response = JOptionPane.showConfirmDialog(parent, message, "Accept", JOptionPane.YES_NO_OPTION);
        return response == JOptionPane.YES_OPTION;
    }

    @Override
    public String getPassphrase() {
        return null;
    }

    @Override
    public boolean promptPassphrase(String message) {
        return false;
    }

    @Override
    public void showMessage(String message) {
        JOptionPane.showMessageDialog(parent, message, "Information", JOptionPane.INFORMATION_MESSAGE);
    }

    public String getUserName() {
        if (user == null)
            user = JOptionPane.showInputDialog(parent, "Enter User Name:", "Enter User", JOptionPane.QUESTION_MESSAGE);
        return user;
    }
}