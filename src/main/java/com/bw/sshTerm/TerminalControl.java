/**
 * Copyright 2025, Bernd Wengenroth.
 * This is free and unencumbered software released into the public domain.
 * Check LICENSE for details.
 */

package com.bw.sshTerm;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Base class to implement handlers for protocol specific Control Sequences.
 */
public abstract class TerminalControl {

    static final boolean debug = true;
    protected ShellChannel term;
    protected TerminalPane pane;
    protected KeyListener keyListener = new KeyAdapter() {

        @Override
        public void keyPressed(KeyEvent e) {
            // TODO: We need to know if the sequences are supported by the terminal...
            try {
                int modifiers = e.getModifiersEx();
                byte[] x = getCtrlCodes((modifiers & KeyEvent.CTRL_DOWN_MASK) != 0 && (modifiers & KeyEvent.ALT_DOWN_MASK) == 0, e.getKeyCode(), e.getKeyChar());
                if (x != null)
                    term.write(x);
                else {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_V:
                            String clipboardText = pane.getClipboardContents();
                            // TODO: Escape if needed
                            term.write(clipboardText.getBytes(StandardCharsets.US_ASCII));
                            break;
                        default:
                            break;
                    }
                }
                e.consume();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        @Override
        public void keyReleased(KeyEvent e) {
            e.consume();
        }

        @Override
        public void keyTyped(KeyEvent e) {
            e.consume();
        }
    };
    private boolean inChars = false;

    protected void log(String message) {
        if (inChars) {
            System.out.print("\n");
            inChars = false;
        }
        System.out.print(message);
    }

    protected void logChar(char c) {
        inChars = true;
        if (c < ' ' || c > 127) {
            System.out.print( c + "[" + Integer.toHexString(c) + "]");
        } else {
            System.out.print(c);
        }

    }

    /**
     * Get control codes for specific keys.
     *
     * @param ctrlDown True if Ctrl was pressed.
     * @param keyCode  The key code.
     * @param keyChar  The key char.
     * @return The control sequence or null.
     */
    public abstract byte[] getCtrlCodes(boolean ctrlDown, int keyCode, char keyChar);

    public abstract String getPtyType();

    /**
     * Handle char and return any answer to the terminal server.
     *
     * @param c The byte to handle.
     * @return the answer to send or null.
     */
    public abstract byte[] handleChar(byte c);

    /**
     * Installs the control to a pane and connects to a terminal-channel.
     *
     * @param pane The pane to show the terminal.
     * @param term The remote shell channel.
     */
    public void install(ShellChannel term, TerminalPane pane) {
        this.term = term;
        pane.addKeyListener(keyListener);
        pane.setFocusTraversalKeysEnabled(false);
        this.pane = pane;
        pane.addPropertyChangeListener(TerminalPane.PROPERTY_TERM_SIZE, evt -> {
            System.out.println("Term Size Changed: " + evt);
            int[] d = (int[]) evt.getNewValue();
            term.setPtySize(d[0], d[1], d[2], d[3]);
        });
        int[] d = pane.getTermSizes();
        term.setPtySize(d[0], d[1], d[2], d[3]);
    }

    /**
     * Handles output from the terminal server.
     *
     * @param buffer    The input buffer
     * @param bytesRead Number of bytes to handle in the buffer.
     * @return answer to send to terminal server or null.
     */
    public byte[] handleShellOutput(byte[] buffer, int bytesRead) {
        ByteArrayOutputStream bs = new ByteArrayOutputStream(100);
        for (int i = 0; i < bytesRead; ++i) {
            byte[] answer = handleChar(buffer[i]);
            if (answer != null)
                bs.write(answer, 0, answer.length);
        }
        if (bs.size() > 0)
            return bs.toByteArray();
        else
            return null;
    }

    /**
     * Gets the terminal pane.
     */
    public TerminalPane getPane() {
        return pane;
    }
}