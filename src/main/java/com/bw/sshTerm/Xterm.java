package com.bw.sshTerm;

import java.awt.event.KeyEvent;
import java.nio.charset.StandardCharsets;

public class Xterm {

    public State state = State.normal;
    public Type type = null;
    public StringBuilder arguments = new StringBuilder();


    byte infix = 0;
    private char[] buffer = new char[1000];

    public byte[] getCtrlCodes(boolean ctrlDown, int keyCode, char keyChar) {
        byte[] data = null;
        if (ctrlDown) {
            switch (keyCode) {
                case KeyEvent.VK_A:
                    data = new byte[]{1}; // Start of Heading (SOH)
                    break;
                case KeyEvent.VK_C:
                    data = new byte[]{3}; // SIGINT
                    break;
                case KeyEvent.VK_D:
                    data = new byte[]{4}; // End of Transmission (EOT)
                    break;
                case KeyEvent.VK_E:
                    data = new byte[]{5}; // Enquiry (ENQ
                    break;
                case KeyEvent.VK_Z:
                    data = new byte[]{26}; // Suspend (SIGTSTP)
                    break;
                case KeyEvent.VK_L:
                    data = new byte[]{12}; // FormFeed
                    break;
                case KeyEvent.VK_X:
                    data = new byte[]{0x18}; // Cancel (CAN)
                    break;
                case KeyEvent.VK_LEFT:
                    data = "\033[1;5D".getBytes(StandardCharsets.US_ASCII); // Left
                    break;
                case KeyEvent.VK_RIGHT:
                    data = "\033[1;5C".getBytes(StandardCharsets.US_ASCII); // Right
                    break;
                default:
                    break;
            }
        } else {
            String s =
                    switch (keyCode) {
                        case KeyEvent.VK_UP -> "\033[A";
                        case KeyEvent.VK_DOWN -> "\033[B";
                        case KeyEvent.VK_RIGHT -> "\033[C";
                        case KeyEvent.VK_LEFT -> "\033[D";
                        case KeyEvent.VK_HOME -> "\033[1~";
                        case KeyEvent.VK_DELETE -> "\033[3~";
                        case KeyEvent.VK_END -> "\033[4~";
                        case KeyEvent.VK_BACK_SPACE -> "\010";
                        default -> null;
                    };
            if (s == null) {
                if (keyChar < 256) {
                    data = new byte[]{(byte) keyChar};
                }
            } else {
                data = s.getBytes(StandardCharsets.US_ASCII);
            }
        }
        return data;
    }

    public String getPtyType() {
        return "xterm";
    }

    protected void addChar(char c, TerminalPane pane) {
        if (pane.getCaretX() >= pane.termWidth) {
            pane.setCaret(0, pane.caretY + 1);
            pane.setChar(c);
        } else {
            pane.setChar(c);
            pane.moveCaret(1, 0);
        }
    }

    public void handleChar(byte c, TerminalPane pane) {
        buffer[0] = 0;
        buffer[1] = 0;
        switch (state) {
            case normal -> {
                switch (c) {
                    case -1, 7 -> {
                    }
                    case 27 -> {
                        state = State.esc;
                        arguments.setLength(0);
                        return;
                    }
                    case 13 -> pane.setCaret(0, pane.caretY);
                    case 8 -> {
                        System.out.println("BS ");
                        pane.moveCaret(-1, 0);
                    }
                    case 10 -> {
                        pane.setCaret(0, pane.caretY + 1);
                    }
                    default -> {
                        addChar((char) c, pane);
                    }
                }
            }
            case esc -> {
                if (c == '[') {
                    type = Type.csi;
                    state = State.typeIndicator;
                    infix = 0;
                } else if (c == ']') {
                    type = Type.osc;
                    state = State.typeIndicator;
                    infix = 0;
                } else if (c == '^') {
                    type = Type.pm;
                    state = State.typeIndicator;
                    infix = 0;
                } else {
                    state = State.normal;
                    addChar('\033', pane);
                    addChar((char) c, pane);
                }
            }
            case typeIndicator -> {
                if ((c >= '0' && c <= '9') || c == ';') {
                    state = State.arguments;
                    arguments.append((char) c);
                } else if (c == '?') {
                    infix = c;
                    state = State.arguments;
                } else {
                    handleCommand(c, arguments.toString(), pane);
                    state = State.normal;
                }
            }
            case arguments -> {
                if ((c >= '0' && c <= '9') || c == ';' ||
                        (type == Type.osc && c >= 32 && c <= 126)) {
                    arguments.append((char) c);
                } else {
                    handleCommand(c, arguments.toString(), pane);
                    state = State.normal;
                    type = null;
                }
            }
        }
    }

    protected void applySgrCode(int code) {
    }

    protected int getIntParameter(int n, int defaultVal, String[] params) {
        return params.length > n && !params[n].isEmpty() ? Integer.parseInt(params[n]) : defaultVal;

    }

    public void handleCommand(byte c, String arguments, TerminalPane pane) {
        String r = null;
        String[] params = arguments.split(";");
        if (params.length == 1 && params[0].isEmpty()) {
            params = new String[]{"0"};
        }

        System.out.println("Command " + type + " " + (infix == 0 ? "" : "" + (char) infix) + (char) c + " {" + arguments + "}");
        if (infix == '?') {
            // Private commands
            if (params.length == 1) {
                int code = Integer.parseInt(params[0]);
                switch (code) {
                    case 1004:
                        break;
                    case 2004:
                        switch (c) {
                            case 'h': // Turn on bracketed paste mode
                                break;
                            case 'l':// Turn off bracketed paste mode
                                break;
                        }
                        break;
                }
            }
            return;
        }
        if (type == Type.csi) {
            switch (c) {
                case 'm'  // SGR - Select Graphic Rendition
                        -> {
                    for (String param : params) {
                        int code = param.isEmpty() ? 0 : Integer.parseInt(param);
                        applySgrCode(code);
                    }
                }
                case 'H' | // CUP - sCursor Position
                             'f' ->  // HVP - Horizontal and Vertical Position (identisch zu CUP)
                {
                    int row = getIntParameter(0, 1, params);
                    int col = getIntParameter(1, 1, params);
                    ;
                    pane.setCaret(col - 1, row - 1);
                }
                case 'J'  // ED - Erase in Display
                        -> {
                    int mode = getIntParameter(0, 0, params);
                    switch (mode) {
                        case 0 -> // Erase Below (default)
                        {
                        }
                        case 1 -> // Erase Above
                        {
                        }
                        case 2 -> // Erase All
                        {
                            pane.clear();
                        }
                        case 3 -> // Erase Saved Lines
                        {
                            // TODO: Currently no concept for "first visible line""
                        }
                        default -> {
                        }
                    }

                }
                case 'K'  // EL - Erase in Line
                        -> {
                    int mode = params.length > 0 && !params[0].isEmpty() ? Integer.parseInt(params[0]) : 0;
                    switch (mode) {
                        case 0: // Erase to Right
                            for (int x = pane.termWidth - 1; x >= pane.caretX; --x)
                                pane.setCharAt(x, pane.caretY, ' ');
                            break;
                        case 1: // Erase to Left
                            break;
                        case 2: // Erase All
                            break;
                    }
                }
                case 'A' // CUU - Cursor Up
                        -> {
                }
                case 'B' // CUD - Cursor Down
                        -> {
                }
                case 'C' -> pane.moveCaret(1, 0);  // CUF - Cursor Forward
                case 'D' -> pane.moveCaret(-1, 0); // CUB - Cursor Backward
                case 'P' // DCH - Delete x Character(s) (default = 1) (DCH)
                        -> {
                    int x = getIntParameter(0, 1, params);
                    while (x > 0) {
                        pane.deleteChar();
                        --x;
                    }
                }
                case '@' // ICH - Insert x (Blank) Character(s) (default = 1)
                        -> {
                    int x = getIntParameter(0, 1, params);
                    while (x > 0) {
                        pane.insert(' ');
                        --x;
                    }

                }
                default -> {
                }
            }
        } else {

        }
    }

    public enum Type {
        // Control Sequence Intro
        csi,
        // Operating System Command
        osc,
        // Private Message (TODO: do we need this?)
        pm;
    }

    public enum State {
        normal,
        esc,
        // type indicator
        typeIndicator,
        arguments
    }

}
