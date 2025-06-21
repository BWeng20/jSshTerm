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
import java.io.PrintStream;

/**
 * Demonstrates usage of the ssh shell.
 */
public class SSHTerm extends JPanel {

    /** The terminal pane thats displays the terminal content.*/
    private final TerminalPane pane = new TerminalPane();

    /** The controller that interprets keys from user and esc control sequences from server.*/
    private final TerminalControl ctrl = new Xterm();

    /** The SSH Shell channel to connect to the server. */
    private ShellChannel channel;

    /**
     * Optional scroll bar the pane can use to control scrollback-buffer.
     */
    private final JScrollBar scroller = new JScrollBar(JScrollBar.VERTICAL);

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

        final Arguments arguments = new Arguments(args);

        JFrame frame = new JFrame("Term" );
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
            term.connect(arguments.login, arguments.password, arguments.host, arguments.port);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Starts a session. The channel needs to be in unconnected state.
     *
     */
    public void connect(String user, String password, String host, int port) {
        try {
            if (channel == null)
                channel = new JschShellChannel();
            channel.connect(user, password, host, port, ctrl);
            pane.requestFocusInWindow();
            ctrl.install(channel, pane);
            pane.setConnected(true, null);
            revalidate();
        } catch (Exception e) {
            e.printStackTrace();
            pane.setConnected(false, "No connection possible!" );
        }

    }

    /**
     * Needs to be called if shell shall be closed.
     */
    public void disconnect() {
        if (this.channel != null) {
            this.channel.disconnect();
            this.channel = null;
            pane.setConnected(false, "Disconnected" );
        }
    }

    /**
     * There are good libs to handle arguments.
     * But as this code is only for demonstration, a dependency would pollute this lib.
     * So let parse manually...
     */
    public static class Arguments {

        final String[] args;
        public String login = null;
        public String password = null;
        public String host = "127.0.0.1";
        public int port = 22;
        int nextArgIndex;


        public Arguments(String[] args) {
            this.args = args;
            nextArgIndex = 0;

            for (String a = nextArgument(); a != null; a = nextArgument()) {
                switch (a) {
                    case "--login", "-l" -> login = getArgValue();
                    case "--secret", "-s" -> password = getArgValue();
                    case "--host", "-h" -> host = getArgValue();
                    case "--port", "-p" -> {
                        try {
                            port = Integer.parseInt(getArgValue());
                        } catch (NumberFormatException ne) {
                            System.err.println("Port argument must be some number." );
                            System.exit(-2);
                        }
                    }
                    case "--help", "-?" -> {
                        usage(System.out);
                        System.exit(0);
                    }
                }
            }

        }

        public static void usage(PrintStream ps) {
            ps.println(
                    """
                            SSHTerm Options:
                            \t--login, -l     Login name, default is current user
                            \t--secret, -s    Password, will be requested if missing
                            \t--host, -h      SSH Server, default 127.0.0.1
                            \t--port, -p      SSH Port, default 22
                            \t--help, -?      Print help and exit
                            
                            Example:
                             java com.bw.sshTerm.SSHTerm -l me -s myPassword -h 1.2.3.4"""
            );
        }

        private String nextArgument() {
            return (nextArgIndex < args.length) ? args[nextArgIndex++].toLowerCase() : null;
        }

        private String getArgValue() {
            if (nextArgIndex < args.length)
                return args[nextArgIndex++];
            System.err.println("Missing value for option," );
            usage(System.err);
            System.exit(-1);
            return null;
        }
    }


}