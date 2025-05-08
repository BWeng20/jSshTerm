package com.bw.sshTerm;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * A very, very simple SSH shell.
 */
public class SSHTerm extends JPanel {

    private ChannelShell channel;
    private final JTextArea text = new JTextArea();
    private byte[] line = new byte[1000];
    private int lineEnd = 0;
    private int caret = 0;
    private int currentLineOffset = 0;
    private OutputStream inputToShell;

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

    public SSHTerm() {
        super(new BorderLayout());
        text.setFont( new Font("Monospaced", Font.PLAIN, 14));
        add( BorderLayout.CENTER, new JScrollPane(text));

        DefaultCaret caret = (DefaultCaret) text.getCaret();
        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);

        text.addKeyListener(new KeyAdapter() {

            @Override
            public void keyPressed(KeyEvent e) {
                try {
                    if ((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0) {
                        try {
                            switch (e.getKeyCode()) {
                                case KeyEvent.VK_C:
                                    inputToShell.write(3); // SIGINT
                                    break;
                                case KeyEvent.VK_D:
                                    inputToShell.write(4); // EOF
                                    break;
                                case KeyEvent.VK_Z:
                                    inputToShell.write(26); // Suspend (SIGTSTP)
                                    break;
                                case KeyEvent.VK_L:
                                    inputToShell.write(12); // FormFeed (Seitenumbruch)
                                    break;
                                case KeyEvent.VK_LEFT:
                                    inputToShell.write("\033[1;5D".getBytes()); // Left
                                    break;
                                case KeyEvent.VK_RIGHT:
                                    inputToShell.write("\033[1;5C".getBytes()); // Right
                                    break;
                                case KeyEvent.VK_V:
                                    try {
                                        String clipboardText = getClipboardContents();
                                        inputToShell.write(clipboardText.getBytes(StandardCharsets.US_ASCII));
                                    } catch (IOException ex) {
                                        ex.printStackTrace();
                                    }
                                default:
                                    break;
                            }
                            inputToShell.flush();
                            e.consume();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    } else {
                        switch (e.getKeyCode()){
                            case KeyEvent.VK_UP -> inputToShell.write("\033[A".getBytes()); // Up
                            case KeyEvent.VK_DOWN -> inputToShell.write("\033[B".getBytes()); // Up
                            case KeyEvent.VK_HOME -> inputToShell.write("\033[1~".getBytes()); // Home
                            case KeyEvent.VK_END -> inputToShell.write("\033[4~".getBytes()); // End
                            case KeyEvent.VK_LEFT -> inputToShell.write("\033[D".getBytes()); // Left
                            case KeyEvent.VK_RIGHT -> inputToShell.write("\033[C".getBytes()); // Left
                            default -> {
                                char c = e.getKeyChar();
                                System.out.print(">"+(int)c);
                                if ( c < 256) {
                                    inputToShell.write(c);
                                }
                            }
                        };
                        inputToShell.flush();
                        e.consume();
                    }

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                e.consume();
            }

            @Override
            public void keyReleased(KeyEvent e) {
                e.consume();
            }

            @Override
            public void keyTyped(KeyEvent e) {
                e.consume();
            }
        });
    }

    public void connect( ChannelShell channel ) throws IOException, JSchException {
        this.channel = channel;
        channel.setPty(true);
        channel.setPtyType("dumb");
        InputStream shellOutput = channel.getInputStream();
        inputToShell = channel.getOutputStream();
        channel.connect();
        Thread rt = new Thread(() -> readShellOutput(shellOutput));
        rt.setDaemon(true);
        rt.start();
    }

    private void readShellOutput(InputStream shellOutput) {
        try {
            byte[] buffer = new byte[1024];
            int bytesRead;
            Document d = text.getDocument();
            while ((bytesRead = shellOutput.read(buffer)) != -1) {
                int o = 0;
                for (int i=0 ; i<bytesRead; ++i) {
                    byte b = buffer[i];
                    switch ( b ) {
                        case -1, 7 -> {
                            System.out.print("["+b+"]");
                        }
                        case 13 -> {
                            caret = 0;
                        }
                        case 8 -> {
                            if ( caret > 0)
                                caret--;
                        }
                        default -> {
                            line[caret++] = b;
                            if ( caret > lineEnd )
                                lineEnd = caret;
                        }
                        case 10 -> {
                            line[lineEnd++] = b;
                            d.remove(currentLineOffset, d.getLength()-currentLineOffset);
                            d.insertString(d.getLength(), new String(line, 0, lineEnd), null);
                            currentLineOffset = d.getLength();
                            caret = 0;
                            lineEnd =0;
                        }
                    }
                }
                d.remove(currentLineOffset, d.getLength()-currentLineOffset);
                d.insertString(d.getLength(), new String(line, 0, lineEnd), null);

                System.out.println("line:"+new String(line, 0, lineEnd));
                text.setCaretPosition(currentLineOffset + caret);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) {

        JFrame frame = new JFrame("Term");
        frame.setLayout(new BorderLayout());

        SSHTerm term = new SSHTerm();
        frame.add(BorderLayout.CENTER, term);
        frame.setPreferredSize(new Dimension(500,500));
        frame.pack();
        frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE);
        frame.setLocationByPlatform(true);
        frame.setVisible(true);

        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession("bernd", "127.0.0.1", 22);
            session.setPassword("bernd");
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();
            term.connect((ChannelShell) session.openChannel("shell"));

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}