package com.bw.sshTerm;

import java.io.IOException;

/**
 * Interface to read and write from a terminal server
 */
public interface ShellChannel {

    /**
     * Writes data to the terminal server.
     */
    void write(byte[] x);

    /**
     * Sets a new terminal size.
     */
    void setPtySize(int termWidth, int termHeight, int charWidth, int charHeight);

    /**
     * Creates and connects a new shell.
     */
    void connect(String user, String password, String host, int port, TerminalControl terminalControl) throws IOException;

    void disconnect();
}
