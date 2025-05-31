/**
 * Copyright 2025, Bernd Wengenroth.
 * This is free and unencumbered software released into the public domain.
 * Check LICENSE for details.
 */

package com.bw.sshTerm;

import javax.swing.text.BadLocationException;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.nio.charset.StandardCharsets;

/**
 * Handles Control Sequences and controls the terminal
 */
public class TerminalControl {

    SSHTerm term;
    TerminalPane pane;
    Xterm termHandler = new Xterm();
    public KeyListener keyListener = new KeyAdapter() {

        @Override
        public void keyPressed(KeyEvent e) {
            // TODO: We need to know if the sequences are supported by the terminal...
            try {
                byte[] x = termHandler.getCtrlCodes((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0, e.getKeyCode(), e.getKeyChar());
                if (x != null)
                    term.write(x);
                else
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_V:
                            String clipboardText = term.getClipboardContents();
                            // TODO: Escape if needed
                            term.write(clipboardText.getBytes(StandardCharsets.US_ASCII));
                            break;
                        default:
                            break;
                    }
                ;
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

    public TerminalControl() {
    }

    public void install(SSHTerm term, TerminalPane pane) {
        this.term = term;
        pane.addKeyListener(keyListener);
        this.pane = pane;
        pane.addPropertyChangeListener(TerminalPane.PROPERTY_TERM_SIZE, evt -> {
            System.out.println("Term Size Changed: " + evt);
            int[] d = (int[]) evt.getNewValue();
            term.setPtySize(d[0], d[1], d[2], d[3]);
        });
        int[] d = pane.getTermSizes();
        term.setPtySize(d[0], d[1], d[2], d[3]);
    }

    protected void handleShellOutput(byte[] buffer, int bytesRead) throws BadLocationException {
        for (int i = 0; i < bytesRead; ++i) {
            termHandler.handleChar(buffer[i], pane);
        }
    }


    public String getPtyType() {
        return termHandler.getPtyType();
    }
}