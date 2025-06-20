/**
 * Copyright 2025, Bernd Wengenroth.
 * This is free and unencumbered software released into the public domain.
 * Check LICENSE for details.
 */

package com.bw.sshTerm;

import com.bw.sshTerm.jsch.JschShellChannel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

/**
 * A simple SSH xterm shell via JSch shell channel.<br>
 * Works for "dumb" or "xterm" terminal mode.
 */
public class SSHTerm extends JPanel {

    String CSI = "\033[";
    private TerminalPane pane = new TerminalPane();
    private TerminalControl ctrl = new Xterm();
    private ShellChannel channel;

    private JScrollBar scroller = new JScrollBar(JScrollBar.VERTICAL);

    /**
     * Create a new terminal. To start a session use {@link #connect}.
     */
    public SSHTerm() {
        super(new BorderLayout());
        add(BorderLayout.CENTER, pane);
        add(BorderLayout.EAST, scroller);

    }

    /**
     * Example for usage of SSHTerm.
     */
    public static void main(String[] args) {

        JFrame frame = new JFrame("Term");
        frame.setLayout(new BorderLayout());

        SSHTerm term = new SSHTerm();

        frame.setContentPane(term);
        frame.setPreferredSize(new Dimension(800, 900));
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationByPlatform(true);

        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                System.out.println("RESIZED");
                term.pane.updateTerminalSpecs();
            }
        });

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                term.disconnect();
                super.windowClosing(e);
            }
        });

        frame.setVisible(true);
        try {
            // Calling with null as user or password will trigger input dialogs,
            // TODO: REPLACE THIS with your arguments or persistent settings.
            term.connect("myUser", "myPassword", "127.0.0.1", 22);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }


    /**
     * Starts a session. The channel needs to be in unconnected state.
     *
     * @throws IOException Throws if JSch fails due to some io-operation.
     */
    public void connect(String user, String password, String host, int port) throws IOException {
        try {
            if (channel == null)
                channel = new JschShellChannel();
            channel.connect(user, password, host, port, ctrl);
            JScrollPane scroller = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
            if (scroller != null) {
                pane.setSize(scroller.getViewport().getSize());
            }
            pane.requestFocusInWindow();
            ctrl.install(channel, pane);
            pane.setConnected(true, null);
            revalidate();
        } catch (Exception e) {
            e.printStackTrace();
            pane.setConnected(false, "No connection possible!");
        }

    }


    /**
     * Needs to be called if shell shall be closed.
     */
    public void disconnect() {
        if (this.channel != null) {
            this.channel.disconnect();
            this.channel = null;
            pane.setConnected(false, "Disconnected");
        }
    }


}