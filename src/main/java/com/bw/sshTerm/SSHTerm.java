/**
 * Copyright 2025, Bernd Wengenroth.
 * This is free and unencumbered software released into the public domain.
 * Check LICENSE for details.
 */

package com.bw.sshTerm;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * A very, very simple SSH shell.<br>
 * Works (currently) only for "dumb" terminal mode.
 * Server have to support ascii control sequences.
 */
public class SSHTerm extends JPanel {

    //private final JScrollPane scroller;
    private final JLabel placeholder;
    String CSI = "\033[";
    private ChannelShell channel;
    private OutputStream inputToShell;
    private InputStream shellOutput;
    private TerminalControl ctrl = new Xterm();
    private TerminalPane pane = new TerminalPane();

    /**
     * Create a new terminal. To start a session use {@link #connect}.
     */
    public SSHTerm() {
        super(new BorderLayout());


        placeholder = new JLabel("<html><h3><i>Not connected</i></h3></html>", JLabel.CENTER);
        add(BorderLayout.CENTER, placeholder);
        // scroller = new JScrollPane(text);

        // scroller.addComponentListener(new ComponentAdapter() {
        //  @Override
        //     public void componentResized(ComponentEvent e) {
        //         pane.updateTerminalSpecs();
        //     }
        // });
    }

    /**
     * Example for usage of SSHTerm.
     */
    public static void main(String[] args) {

        JFrame frame = new JFrame("Term");
        frame.setLayout(new BorderLayout());

        SSHTerm term = new SSHTerm();

        JScrollPane scroller = new JScrollPane(term);
        scroller.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scroller.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        frame.add(BorderLayout.CENTER, scroller);
        frame.add(BorderLayout.SOUTH, new JTextArea("AAA"));
        frame.setPreferredSize(new Dimension(800, 900));
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationByPlatform(true);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                term.disconnect();
                super.windowClosing(e);
            }
        });

        frame.setVisible(true);

        try {
            JSch jsch = new JSch();
            UserInfo userInfo = new UserInfo(term);
            Session session = // jsch.getSession(userInfo.getUserName(), "127.0.0.1", 22);
                    jsch.getSession("bernd", "127.0.0.1", 22);
            session.setPassword("bernd");
            session.setUserInfo(userInfo);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();
            term.connect((ChannelShell) session.openChannel("shell"));

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    protected String getClipboardContents() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable contents = clipboard.getContents(null);
            if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                return (String) contents.getTransferData(DataFlavor.stringFlavor);
            }
        } catch (Exception e) {
        }
        return "";
    }

    /**
     * Starts a session. The channel needs to be in unconnected state.<br>
     * <b>Example</b>
     * <pre>
     *   JSch jsch = new JSch();
     *   Session session = jsch.getSession("me", "127.0.0.1", 22);
     *   session.setPassword("password123");
     *   session.setConfig("StrictHostKeyChecking", "no");
     *   session.connect();
     *   SSHTerm term = new SSHTerm();
     *   term.connect((ChannelShell)session.openChannel("shell"));
     * </pre>
     *
     * @param channel The channel.
     * @throws IOException   Throws if JSch fails due to some io-operation.
     * @throws JSchException Throws if JSch fails due to e.g. password or missing ssh server.
     */
    public void connect(ChannelShell channel) throws IOException, JSchException {
        this.channel = channel;
        channel.setPty(true);
        channel.setPtyType(ctrl.getPtyType());
        add(BorderLayout.CENTER, pane);
        revalidate();
        pane.updateTerminalSpecs();

        shellOutput = channel.getInputStream();
        inputToShell = channel.getOutputStream();
        channel.connect();

        remove(placeholder);
        pane.requestFocusInWindow();
        ctrl.install(this, pane);

        Thread rt = new Thread(this::readShellOutput);
        rt.setDaemon(true);
        rt.start();
    }

    public void write(int data) {
        if (channel != null) {
            try {
                inputToShell.write(data);
                inputToShell.flush();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    public void write(String data) {
        write(data.getBytes(StandardCharsets.US_ASCII));
    }

    public void write(byte[] data) {
        if (channel != null) {
            try {
                inputToShell.write(data);
                inputToShell.flush();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }


    protected void readShellOutput() {
        try {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = shellOutput.read(buffer)) != -1) {
                ctrl.handleShellOutput(buffer, bytesRead);
            }
            System.out.println("Connection terminated");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Needs to be called if shell shall be closed.
     */
    public void disconnect() {
        if (this.channel != null) {
            this.channel.disconnect();
            this.channel = null;
            // remove(scroller);
            add(BorderLayout.CENTER, placeholder);
        }
    }


    public void setPtySize(int termWidth, int termHeight, int charWidth, int charHeight) {
        if (channel != null)
            channel.setPtySize(termWidth, termHeight, charWidth, charHeight);
    }
}