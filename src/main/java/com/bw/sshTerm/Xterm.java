package com.bw.sshTerm;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * A Terminal controller that supports xterm control sequences.
 */
public class Xterm extends TerminalControl {

    public static final Charset asciiCharset = StandardCharsets.US_ASCII;
    int uft8Codepoint = 0;
    private State state = State.normal;
    private int utf8BytesLeft = 0;
    private Type type = null;
    private StringBuilder arguments = new StringBuilder();
    private int infix = 0;
    private boolean bracketedPasteMode = false;

    public byte[] getCtrlCodes(boolean ctrlDown, int keyCode, char keyChar) {
        byte[] data = null;
        if (ctrlDown) {
            if (debug)
                log("Ctrl Code " + keyCode + "(0x" + Integer.toHexString(keyCode) + ") char " + keyChar + " (" + ((int) keyChar) + ")\n");
            data =
                    switch (keyCode) {
                        case KeyEvent.VK_A -> new byte[]{1}; // Start of Heading (SOH)
                        case KeyEvent.VK_B -> new byte[]{2}; // Start of Text (STX)
                        case KeyEvent.VK_C -> new byte[]{3}; // ETX (End of Text)
                        case KeyEvent.VK_D -> new byte[]{4}; // End of Transmission (EOT)
                        case KeyEvent.VK_E -> new byte[]{5}; // Enquiry (ENQ
                        case KeyEvent.VK_F -> new byte[]{6}; // ACK (Acknowledge)
                        case KeyEvent.VK_G -> new byte[]{7}; // BEL (Bell)
                        case KeyEvent.VK_H -> new byte[]{8}; // BS (Backspace)
                        case KeyEvent.VK_I -> new byte[]{9}; // HT (Horizontal Tab)
                        case KeyEvent.VK_J -> new byte[]{10}; // LF (Line Feed)
                        case KeyEvent.VK_K -> new byte[]{11}; // VT (Vertical Tabulation)
                        case KeyEvent.VK_L -> new byte[]{12}; // FF (Form Feed)
                        case KeyEvent.VK_M -> new byte[]{13}; // CR (Carriage Return)
                        case KeyEvent.VK_N -> new byte[]{14}; // SO (Shift Out)
                        case KeyEvent.VK_O -> new byte[]{15}; // SI (Shift In)
                        case KeyEvent.VK_P -> new byte[]{16}; // DLE (Data Link Escape)
                        case KeyEvent.VK_Q -> new byte[]{0x11}; // DC1	(Device Control One (X-On))
                        case KeyEvent.VK_R -> new byte[]{0x12}; // DC2	(Device Control Two)
                        case KeyEvent.VK_S -> new byte[]{0x13}; // DC3	(Device Control Three (XOFF))
                        case KeyEvent.VK_T -> new byte[]{0x14}; // DC4	(Device Control Four
                        case KeyEvent.VK_U -> new byte[]{0x15}; // NAK	(Negative Acknowledge)
                        case KeyEvent.VK_V -> new byte[]{0x16}; // SYN	(Synchronous Idle)
                        case KeyEvent.VK_W -> new byte[]{0x17}; // ETB	(End of Transmission Block)
                        case KeyEvent.VK_X -> new byte[]{0x18}; // CAN	(Cancel)
                        case KeyEvent.VK_Y -> new byte[]{0x19}; // EM	(End of medium)
                        case KeyEvent.VK_Z -> new byte[]{0x1A}; // SUB	(Substitute) / Suspend (SIGTSTP)
                        case KeyEvent.VK_SPACE -> new byte[]{0};
                        case KeyEvent.VK_LEFT -> "\033[1;5D".getBytes(asciiCharset); // Left
                        case KeyEvent.VK_RIGHT -> "\033[1;5C".getBytes(asciiCharset); // Right
                        default -> switch (keyChar) {
                            case '[' -> new byte[]{0x1B}; // ESC (Escape)
                            case '\\' -> new byte[]{0x1C};// FS (File Separator)
                            case ']' -> new byte[]{0x1D}; // GS (Group Separator)
                            case '~' -> new byte[]{0x1E}; // RS (Record Separator)
                            default -> null;
                        };
                    };
        } else {
            if (debug)
                log("Code " + keyCode + "(0x" + Integer.toHexString(keyCode) + ") char " + keyChar + " (" + ((int) keyChar) + ")\n");
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
                        case KeyEvent.VK_ENTER -> "\015";
                        default -> null;
                    };
            if (s == null) {
                if (keyChar != KeyEvent.CHAR_UNDEFINED)
                    data = (new String(new char[]{keyChar})).getBytes(StandardCharsets.UTF_8);
            } else {
                data = s.getBytes(asciiCharset);
            }
        }
        return data;
    }

    @Override
    public String getPtyType() {
        return "xterm";
    }

    protected void addChar(char c) {
        if (pane.getCaretX() >= pane.termWidth) {
            pane.setCaretAbsolute(0, pane.getCaretY() + 1);
            pane.setChar(c);
        } else {
            pane.setChar(c);
            pane.moveCaret(1, 0);
        }
    }

    @Override
    public byte[] handleChar(byte c) {
        byte[] answer = null;
        switch (state) {
            case normal -> {
                switch (c) {
                    case -1, 7 -> {
                    }
                    case 27 -> {
                        state = State.esc;
                        arguments.setLength(0);
                    }
                    case 13 -> pane.setCaretAbsolute(0, pane.getCaretY());
                    case 8 -> {
                        if (debug) log("BS ");
                        pane.moveCaret(-1, 0);
                    }
                    case 10 -> pane.setCaretAbsolute(0, pane.getCaretY() + 1);
                    default -> {
                        if (debug) logChar((char) c);
                        if ((c & 0b10000000) == 0) {
                            // 1-Byte ASCII
                            addChar((char) c);
                            uft8Codepoint = 0;
                            utf8BytesLeft = 0;
                        } else if ((c & 0b11000000) == 0b10000000) {
                            // Part of Utf8 sequence
                            uft8Codepoint <<= 6;
                            uft8Codepoint |= 0x3F & c;
                            if ((--utf8BytesLeft) == 0) {
                                if (Character.isBmpCodePoint(uft8Codepoint)) {
                                    if (debug) log("utf8 char " + ((char) uft8Codepoint) + "\n");
                                    addChar((char) uft8Codepoint);
                                } else {
                                    log("Characters outside BMP range are not supported: 0x" + Integer.toHexString(uft8Codepoint) + "\n");
                                    addChar('?');
                                }
                            }
                        } else if ((c & 0b11100000) == 0b11000000) {
                            // 2-Byte utf8
                            uft8Codepoint = 0x1F & c;
                            utf8BytesLeft = 1;
                        } else if ((c & 0b11110000) == 0b11100000) {
                            // 3-Byte utf8
                            uft8Codepoint = 0x0F & c;
                            utf8BytesLeft = 2;
                        } else if ((c & 0b11111000) == 0b11110000) {
                            // 4-Byte utf8
                            uft8Codepoint = 0x07 & c;
                            utf8BytesLeft = 3;
                        } else {
                            uft8Codepoint = 0;
                            utf8BytesLeft = 0;
                            log("Illegal utf8 start byte 0x" + Integer.toHexString(0x00FF & c));
                        }
                    }
                }
            }
            case esc -> {
                switch ((char) c) {
                    case '[' -> {
                        type = Type.csi;
                        state = State.typeIndicator;
                        infix = 0;
                    }
                    case ']' -> {
                        type = Type.osc;
                        state = State.typeIndicator;
                        infix = 0;
                    }
                    case '^' -> {
                        type = Type.pm;
                        state = State.typeIndicator;
                        infix = 0;
                    }
                    case '7', '8', '=', '>', 'D', 'E', 'F', 'H', 'M', 'c', 'l', 'm', 'n', 'o', '|', '}', '~' -> {
                        handleEscCommand(c, (byte) 0);
                        state = State.normal;
                        infix = 0;
                    }
                    case ' ', '#', '%', '(', ')', '*', '+' -> {
                        infix = c;
                        state = State.escSingleChar;
                        type = Type.escSingleChar;
                    }
                    default -> {
                        state = State.normal;
                        addChar('\033');
                        addChar((char) c);
                    }
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
                    answer = handleCommand(c);
                    state = State.normal;
                }
            }
            case escSingleChar -> {
                // Esc with one single character. First char in infix
                answer = handleCommand(c);
                state = State.normal;
            }
            case arguments -> {
                if ((c >= '0' && c <= '9') || c == ';' ||
                        (type == Type.osc && c >= 32 && c <= 126)) {
                    arguments.append((char) c);
                } else {
                    answer = handleCommand(c);
                    state = State.normal;
                    type = null;
                }
            }
        }
        return answer;
    }

    protected void applySgrCode(int code) {
        if (debug) log(" -> Sgr " + code);
        switch (code) {
            case 0 -> { // Normal (default)
                pane.setCharBackground(null);
                pane.setCharForeground(null);
                pane.setCharStyle(0);
            }
            case 1 -> // Bold
                    pane.setCharStyle(CharStyle.BOLD);
            case 4 -> // Underlined
                    pane.setCharStyle(CharStyle.UNDERLINED);
            case 5 -> // Blink (appears as Bold)
                    pane.setCharStyle(CharStyle.BOLD);
            case 7 -> { // Inverse
                pane.setCharBackground(pane.getForeground());
                pane.setCharForeground(pane.getBackground());
            }
            case 8 -> { // Invisible, i.e., hidden (VT300)
            }
            case 22 -> // Normal (neither bold nor faint)
                    pane.setCharStyle(0);
            case 24 -> // Not underlined
                    pane.clearCharStyle(CharStyle.UNDERLINED);
            case 25 -> // Steady (not blinking)
                    pane.clearCharStyle(CharStyle.BOLD);
            case 27 -> { // Positive (not inverse)
                Color bg = pane.getCharBackground();
                pane.setCharBackground(pane.getCharForeground());
                pane.setCharForeground(bg);
            }
            case 28 -> { // Visible, i.e., not hidden (VT300)
            }
            case 30 -> // Set foreground color to Black
                    pane.setCharForeground(Color.BLACK);
            case 31 -> // Set foreground color to Red
                    pane.setCharForeground(Color.RED);
            case 32 -> // Set foreground color to Green
                    pane.setCharForeground(Color.GREEN);
            case 33 -> // Set foreground color to Yellow
                    pane.setCharForeground(Color.YELLOW);
            case 34 -> // Set foreground color to Blue
                    pane.setCharForeground(Color.BLUE);
            case 35 -> // Set foreground color to Magenta
                    pane.setCharForeground(Color.MAGENTA);
            case 36 -> // Set foreground color to Cyan
                    pane.setCharForeground(Color.CYAN);
            case 37 -> // Set foreground color to White
                    pane.setCharForeground(Color.WHITE);
            case 39 -> // Set foreground color to default (original)
                    pane.setCharForeground(null);
            case 40 -> // Set background color to Black
                    pane.setCharBackground(Color.BLACK);
            case 41 -> // Set background color to Red
                    pane.setCharBackground(Color.RED);
            case 42 -> // Set background color to Green
                    pane.setCharBackground(Color.GREEN);
            case 43 -> // Set background color to Yellow
                    pane.setCharBackground(Color.YELLOW);
            case 44 -> // Set background color to Blue
                    pane.setCharBackground(Color.BLUE);
            case 45 -> // Set background color to Magenta
                    pane.setCharBackground(Color.MAGENTA);
            case 46 -> // Set background color to Cyan
                    pane.setCharBackground(Color.CYAN);
            case 47 -> // Set background color to White
                    pane.setCharBackground(Color.WHITE);
            case 49 -> // Set background color to default (original).
                    pane.setCharBackground(null);
        }
    }

    protected int getIntParameter(int n, int defaultVal, String[] params) {
        return params.length > n && !params[n].isEmpty() ? Integer.parseInt(params[n]) : defaultVal;
    }

    protected String getTextParameter(int n, String defaultVal, String[] params) {
        return params.length > n && !params[n].isEmpty() ? params[n] : defaultVal;
    }

    protected byte[] handleCsiCommand(int c, String[] params) {
        if (debug)
            log("Command CSI " + (infix == 0 ? "" : "" + (char) infix) + (c >= 32 ? "'" + ((char) c) + "'" : String.valueOf(c)) + " {" + String.join(",", params) + "}");
        byte[] response = null;
        switch ((char) c) {
            case 'c' -> {
                // Send Device Attributes (Primary DA).
                int ps = getIntParameter(0, 0, params);
                switch (ps) {
                    case 0 -> //   Request attributes from terminal.
                        //   -> CSI?1;2c = VT100 with Advanced Video Option
                            response = "CSI?1;2c".getBytes(asciiCharset);
                }
            }
            case 'd' -> { // VPA Move to the corresponding vertical position (line Ps) of the current column (default 1).
                int ps = getIntParameter(0, 1, params) - 1;
                if (debug) log(" -> Move caret to " + pane.getCaretX() + "," + ps);
                pane.setCaretAbsolute(pane.getCaretX(), ps);
            }
            case 'h' -> {
                if (infix == '?') {
                    // DEC Private Mode Set (DECSET)
                    for (String p : params) {
                        int ps = Integer.parseInt(p);

                        switch (ps) {
                            case 1 -> { //  Application Cursor Keys (DECCKM)
                                if (debug) log(" -> Application Cursor Keys (NI)");
                            }
                            case 2 -> { //  Designate USASCII for character sets G0-G3 (DECANM), and set VT100 mode.
                                if (debug)
                                    log(" -> Designate USASCII for character sets G0-G3 (DECANM), and set VT100 mode (NI)");
                            }
                            case 3 -> { //  132 Column Mode (DECCOLM)
                                if (debug) log(" -> 132 Column Mode (NI)");
                            }
                            case 4 -> { //  Smooth (Slow) Scroll (DECSCLM)
                                if (debug) log(" -> Smooth (Slow) Scroll (NI)");
                            }
                            case 5 -> { //  Reverse Video (DECSCNM)
                                if (debug) log(" -> Reverse Video (NI)");
                            }
                            case 6 -> { //  Origin Mode (DECOM)
                                if (debug) log(" ->  Origin Mode (NI)");
                            }
                            case 7 -> { //  Wraparound Mode (DECAWM)
                                if (debug) log(" ->  Wraparound Mode (NI)");
                            }
                            case 8 -> { //  Auto-repeat Keys (DECARM)
                                if (debug) log(" -> Auto-repeat Keys (NI)");
                            }
                            case 9 -> { //  Send Mouse X & Y on button press. See the section Mouse Tracking.
                                if (debug) log(" -> Send Mouse X & Y on button press (NI)");
                            }
                            case 10 -> { //  Show toolbar (rxvt)
                                if (debug) log(" -> Show toolbar (NI)");
                            }
                            case 12 -> //  Start Blinking Cursor (att610)
                            {
                                if (debug) log(" -> Start Blinking Cursor (NI)");
                            }
                            case 18 -> { //  Print form feed (DECPFF)
                                if (debug) log(" ->  Print form feed (NI)");
                            }
                            case 19 -> { //  Set print extent to full screen (DECPEX)
                                if (debug) log(" ->  Set print extent to full screen (NI)");
                            }
                            case 25 -> { //  Show Cursor (DECTCEM)
                                if (debug) log(" -> Show Cursor");
                                pane.showCursor = true;
                            }
                            case 30 -> { //  Show scrollbar (rxvt).
                                if (debug) log(" -> Show Scrollbar (NI)");
                            }
                            case 35 -> { //  Enable font-shifting functions (rxvt).
                                if (debug) log(" -> Enable font-shifting (NI)");
                            }
                            case 38 -> { //  Enter Tektronix Mode (DECTEK)
                            }
                            case 40 -> { //  Allow 80 → 132 Mode
                            }
                            case 41 -> { //  more(1) fix (see curses resource)
                            }
                            case 42 -> { //  Enable Nation Replacement Character sets (DECNRCM)
                            }
                            case 44 -> { //  Turn On Margin Bell
                            }
                            case 45 -> { //  Reverse-wraparound Mode
                            }
                            case 46 -> { //  Start Logging (normally disabled by a compile-time option)
                                // Disabled
                            }
                            case 47,
                                 1047 -> //  Use Alternate Screen Buffer (unless disabled by the titeInhibit resource)
                                    pane.switchScreen(1, false);
                            case 66 -> { //  Application keypad (DECNKM)
                            }
                            case 67 -> { //  Backarrow key sends backspace (DECBKM)
                            }
                            case 1000 -> { // Send Mouse X & Y on button press and release. See the section Mouse Tracking.
                            }
                            case 1001 -> { // Use Hilite Mouse Tracking.
                            }
                            case 1002 -> { // Use Cell Motion Mouse Tracking.
                            }
                            case 1003 -> { // Use All Motion Mouse Tracking.
                            }
                            case 1010 -> { // Scroll to bottom on tty output (rxvt).
                            }
                            case 1011 -> { // Scroll to bottom on key press (rxvt).
                            }
                            case 1035 -> { // Enable special modifiers for Alt and NumLock keys.
                            }
                            case 1036 -> { // Send ESC when Meta modifies a key (enables the metaSendsEscape resource).
                            }
                            case 1037 -> { // Send DEL from the editing-keypad Delete key
                            }
                            case 1048 -> { // Save cursor as in DECSC (unless disabled by the titeInhibit resource)
                            }
                            case 1049 -> {
                                // Save cursor as in DECSC and use Alternate Screen Buffer,
                                // clearing it first (unless disabled by the titeInhibit resource).
                                // This combines the effects of the 1047 and 1048 modes.
                                pane.switchScreen(1, false);
                                pane.clear();
                            }
                            case 1051 -> { // Set Sun function-key mode.
                            }
                            case 1052 -> { // Set HP function-key mode.
                            }
                            case 1053 -> { // Set SCO function-key mode.
                            }
                            case 1060 -> { // Set legacy keyboard emulation (X11R6).
                            }
                            case 1061 -> { // Set Sun/PC keyboard emulation of VT220 keyboard.
                            }
                            case 2004 -> { // Set bracketed paste mode.
                                if (debug) log(" -> bracketedPasteMode on");
                                bracketedPasteMode = true;
                            }
                        }
                    }

                } else {
                    // Nothing for now.
                }
            }
            case 'l' -> {
                if (infix == '?') {
                    // DEC Private Mode Reset (DECRST)
                    for (String p : params) {
                        int ps = Integer.parseInt(p);
                        switch (ps) {
                            case 1 -> // Normal Cursor Keys (DECCKM).
                            {
                                if (debug) log(" -> Normal Cursor Keys (NI)");
                            }
                            case 2 -> { // Designate VT52 mode (DECANM).
                                if (debug) log(" -> Designate VT52 mode (NI)");
                            }
                            case 3 -> { // 80 Column Mode (DECCOLM).
                                if (debug) log(" -> 80 Column Mode (NI)");
                            }
                            case 6 -> { // Normal Cursor Mode (DECOM).
                                if (debug) log(" -> Normal Cursor Mode (NI)");
                            }
                            case 7 -> { // No Wraparound Mode (DECAWM).
                                if (debug) log(" -> No Wraparound Mode (NI)");
                            }
                            case 8 -> { // No Auto-repeat Keys (DECARM).
                                if (debug) log(" -> No Auto-repeat Keys (NI)");
                            }
                            case 9 -> { // Don’t send Mouse X & Y on button press.
                                if (debug) log(" -> Don’t send Mouse X & Y on button press (NI)");
                            }
                            case 12 -> // Stop Blinking Cursor.
                            {
                                if (debug)
                                    log(" -> Stop Blinking Cursor (NI)");
                            }
                            case 25 -> { // Hide Cursor (DECTCEM).
                                if (debug) log(" -> Hide Cursor\n");
                                pane.showCursor = false;
                            }
                            case 45 -> { // No reverse wrap-around.
                                if (debug) log(" -> No reverse wrap-around (NI)");
                            }
                            case 47 -> // Use Normal Screen Buffer.
                            {
                                if (debug) log(" -> Use Normal Screen Buffer");
                                pane.switchScreen(0, false);
                            }
                            case 66 -> { // Numeric keypad (DECNKM).
                                if (debug) log(" -> Numeric keypad (NI)");
                            }
                            case 1000 -> { // Don’t send Mouse reports.
                                if (debug) log(" -> Don’t send Mouse reports (NI)");
                            }
                            case 1002 -> { // Don’t use Cell Motion Mouse Tracking.
                                if (debug) log(" -> Don’t use Cell Motion Mouse Tracking (NI)");
                            }
                            case 1003 -> { // Don’t use All Motion Mouse Tracking.
                                if (debug) log(" -> Don’t use All Motion Mouse Tracking (NI)");
                            }
                            case 1004 -> { // Don’t send FocusIn/FocusOut events.
                                if (debug) log(" -> Don’t send FocusIn/FocusOut events (NI)");
                            }
                            case 1005 -> { // Disable UTF-8 Mouse Mode.
                                if (debug) log(" -> Disable UTF-8 Mouse Mode (NI)");
                            }
                            case 1006 -> { // Disable SGR Mouse Mode.
                                if (debug) log(" -> Disable SGR Mouse Mode (NI)");
                            }
                            case 1015 -> { // Disable urxvt Mouse Mode.
                                if (debug) log(" -> Disable urxvt Mouse Mode (NI)");
                            }
                            case 1016 -> { // Disable SGR-Pixels Mouse Mode.
                                if (debug) log(" -> Disable SGR-Pixels Mouse Mode (NI)");
                            }
                            case 1047 -> { // Use Normal Screen Buffer (clearing screen if in alt).
                                if (debug) log(" -> Use Normal Screen Buffer (clearing screen if in alt)");
                                if (pane.getActiveScreen() == 1) {
                                    pane.switchScreen(0, false);
                                    pane.clear();
                                }
                            }
                            case 1048 -> { // Restore cursor as in DECRC.
                            }
                            case 1049 -> { // Use Normal Screen Buffer and restore cursor.
                                if (debug) log(" -> Use Normal Screen Buffer and restore cursor");
                                if (pane.getActiveScreen() == 1) {
                                    pane.switchScreen(0, true);
                                }
                            }
                            case 2004 -> { // Reset bracketed paste mode.
                                if (debug) log(" -> bracketedPasteMode off");
                                bracketedPasteMode = false;
                            }
                        }
                    }
                } else {
                    // Reset various terminal attributes.
                    for (String p : params) {
                        int ps = Integer.parseInt(p);
                        switch (ps) {
                            case 2 -> // keyboard Action Mode (KAM).
                            {
                            }
                            case 4 -> // Replace Mode (IRM).
                            {
                            }
                            case 12 -> // Send/receive (SRM).
                            {

                            }
                            case 20 -> // Normal Linefeed (LNM)
                            {
                            }
                        }
                    }
                }
            }
            case 'm'  // SGR - Select Graphic Rendition
                    -> {
                if (debug) log(" -> Select character attributes");
                if (params.length == 0) {
                    applySgrCode(0);
                } else {
                    for (String param : params) {
                        int code = param.isEmpty() ? 0 : Integer.parseInt(param);
                        applySgrCode(code);
                    }
                }
            }
            case 'r' // DECSTBM
                    -> {
                if (debug) log(" -> Set top and bottom margins");
                pane.setMargins(getIntParameter(0, 1, params) - 1, getIntParameter(1, pane.termHeight, params) - 1);
                // Set Scrolling Region
                // Set top and bottom margins.
                //   Ps1    Line number for the top margin.
                //          The default value is 1.
                //   Ps2    Line number for the bottom margin.
                //          The default value is current number of lines per screen.
            }
            case 't' -> { // Window manipulation
                for (String param : params) {
                    int code = param.isEmpty() ? 0 : Integer.parseInt(param);
                    switch (code) {
                        case 1 -> { // De-iconify window.
                        }
                        case 2 -> { // Iconify window.
                        }
                        case 3 -> { // ; x ; y → Move window to [x, y].
                        }
                        case 4 -> { // ; height ; width → Resize the xterm window to height and width in pixels.
                        }
                        case 5 -> { // Raise the xterm window to the front of the stacking order.
                        }
                        case 6 -> { // Lower the xterm window to the bottom of the stacking order.
                        }
                        case 7 -> { // Refresh the xterm window.
                        }
                        case 8 -> { //; height ; width → Resize the text area to [height;width] in characters.
                        }
                        case 9 -> {
                            //; 0 → Restore maximized window.
                            //; 1 → Maximize window (i.e., resize to screen size).
                        }
                        case 11 -> { // Report xterm window state. If the xterm window is open (non-iconified), it returns CSI 1 t . If the xterm window is iconified, it returns CSI 2 t .
                        }
                        case 13 -> { // Report xterm window position as CSI 3 ; x; yt
                        }
                        case 14 -> { // Report xterm window in pixels as CSI 4 ; height ; width t
                        }
                        case 18 -> { // Report the size of the text area in characters as CSI 8 ; height ; width t
                        }
                        case 19 -> { // Report the size of the screen in characters as CSI 9 ; height ; width t
                        }
                        case 20 -> { // Report xterm window’s icon label as OSC L label S
                        }
                        case 21 -> { // Report xterm window’s title as OSC l title S
                        }
                        case 22 -> //   Save window title on stack.
                        {
                            // Ps2 = 0, 1, 2    Save window title.
                            if (debug) log(" -> Save window title on stack (NI)");
                        }
                        case 23 -> //     Restore window title from stack.
                        {
                            // Ps2 = 0, 1, 2    Restore window title.
                            if (debug) log(" -> Restore window title from stack (NI)");
                        }
                        default -> {
                            // >= 2 4 → Resize to P s lines (DECSLPP)
                        }
                    }
                }

            }
            case 'A' // CUU - Cursor Up
                    -> {
                if (debug) log(" -> Cursor Up");
                pane.setCaretAbsolute(pane.getCaretX(), pane.getCaretY() - 1);
            }
            case 'B' // CUD - Cursor Down
                    -> {
                if (debug) log(" -> Cursor Down");
                pane.setCaretAbsolute(pane.getCaretX(), pane.getCaretY() + 1);
            }
            case 'C' -> pane.moveCaret(1, 0);  // CUF - Cursor Forward
            case 'D' -> pane.moveCaret(-1, 0); // CUB - Cursor Backward
            case 'G' -> {
                // Cursor Character Absolute [column] (default = [row,1]) (CHA)
                // Moves cursor to the Ps-th column of the active line. The default value of Ps is 1.
                int ps = getIntParameter(0, 1, params) - 1;
                if (debug) log(" -> Move caret to " + ps + "," + pane.getCaretY());
                pane.setCaretAbsolute(ps, pane.getCaretY());
            }
            case 'f', // HVP - Horizontal and Vertical Position (identisch zu CUP)
                 'H' -> // CUP - sCursor Position
            {
                // Moves cursor to the Ps1-th line and to the Ps2-th column. The default value of Ps1 and Ps2 is 1.
                int row = getIntParameter(0, 1, params) - 1;
                int col = getIntParameter(1, 1, params) - 1;
                if (debug) log(" -> Move caret to " + col + "," + row);
                pane.setCaretAbsolute(col, row);
            }
            case 'J'  // ED - Erase in Display
                    -> {
                int mode = getIntParameter(0, 0, params);
                switch (mode) {
                    case 0 -> // Erase Below (default)
                    {
                        if (debug) log(" -> Erase Below (NI)");
                    }
                    case 1 -> // Erase Above
                    {
                        if (debug) log(" -> Erase Above (NI)");
                    }
                    case 2 -> // Erase All
                    {
                        if (debug) log(" -> Erase All");
                        pane.clear();
                    }
                    case 3 -> // Erase Saved Lines
                    {
                        if (debug) log(" -> Erase Saved Lines (NI)");
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
                        if (debug) log(" -> Erase to Right");
                        for (int x = pane.termWidth - 1; x >= pane.getCaretX(); --x)
                            pane.setCharAt(x, pane.getCaretY(), ' ');
                        break;
                    case 1: // Erase to Left
                        if (debug) log(" -> Erase to Left (NI)");
                        break;
                    case 2: // Erase All
                        if (debug) log(" -> Erase All (NI)");
                        break;
                }
            }
            case 'P' // DCH - Delete x Character(s) (default = 1) (DCH)
                    -> {
                int x = getIntParameter(0, 1, params);
                if (debug) log(" -> Delete " + x + " Characters");
                while (x > 0) {
                    pane.deleteChar();
                    --x;
                }
            }
            case '@' // ICH - Insert x (Blank) Character(s) (default = 1)
                    -> {
                int x = getIntParameter(0, 1, params);
                if (debug) log(" -> Insert " + x + " blank Characters");
                while (x > 0) {
                    pane.insert(' ');
                    --x;
                }

            }
            default -> {
            }
        }
        if (debug) log("\n");
        return response;
    }

    protected byte[] handleOscCommand(int c, String[] params) {
        if (debug)
            log("Command OSC " + (infix == 0 ? "" : "" + (char) infix) + (c >= 32 ? "'" + ((char) c) + "'" : String.valueOf(c)) + " {" + String.join(",", params) + "}");
        switch (c) {
            case 7, (byte) 0x9C -> // BELL or ST: Set Text Parameters
            {
                int ps = getIntParameter(0, 0, params);
                String pt = getTextParameter(1, "", params);

                switch (ps) {
                    case 0 -> // Change Icon Name and Window Title to pt
                    {
                        if (debug) log(" -> Change Icon Name and Window Title (NI)");
                        pane.setTitle(pt);
                    }
                    case 1 -> { // Change Icon Name to pt
                        if (debug) log(" -> Change Icon Name to pt (NI)");

                    }
                    case 2 -> { // Change Window Title to pt
                        if (debug) log(" -> Change Window Title to pt (NI)");

                    }
                    case 3 -> {
                        // Set X property on top-level window.
                        // Pt should be in the form "prop=value", or just "prop" to delete the property
                        if (debug) log(" -> Set X property on top-level window (NI)");
                    }
                    case 4 -> {
                        // pt=c;spec;... Change color number c to the color specified by spec.
                        // The color numbers correspond to the
                        // + ANSI colors 0-7,
                        // + their bright versions 8-15, and if supported,
                        // + the remainder of the 88-color or 256-color table.
                        // TODO: For "?" as spec, a response is needed
                        if (debug) log(" -> Change color number (NI)");
                    }
                    case 10, 11, 12, 13, 14, 15, 16, 17, 18 -> {
                        // Dynamic colors
                        if (debug) log(" -> Dynamic colors (NI)");
                    }
                    case 46 -> {
                        // Change Log File to pr
                        if (debug) log(" -> Change Log File to pr (disabled)");
                    }
                    case 50 -> {
                        // Set Font to pt - nope!
                        if (debug) log(" -> Set Font to pt (disabled)");
                    }
                    case 52 -> {
                        // Manipulate Selection Data (disabled)
                    }
                }
            }
        }
        if (debug) log("\n");
        return null;
    }

    /**
     * Simple 2 char commands
     */
    public byte[] handleEscCommand(int first, int second) {
        if (debug) log("Command ESC" + ((char) first) + (second == 0 ? "" : " " + (char) second) + " -> ");

        switch (first) {
            case ' ' -> {
                switch (second) {
                    case 'F' -> {
                        if (debug)
                            log("7-bit controls (S7C1T)\n");
                    }
                    case 'H' -> {
                        if (debug) log("8-bit controls (S8C1T)\n");
                    }
                    case 'L' -> {
                        if (debug) log("Set ANSI conformance level 1 (dpANS X3.134.1)\n");
                    }
                    case 'M' -> {
                        if (debug) log("Set ANSI conformance level 2 (dpANS X3.134.1)\n");
                    }
                    case 'N' -> {
                        if (debug) log("Set ANSI conformance level 3 (dpANS X3.134.1)\n");
                    }
                    default -> {
                        if (debug) log("Unknown\n");
                    }
                }

            }
            case '#' -> {
                switch (second) {
                    case '3' -> {
                        if (debug) log("DEC double-height line, top half (DECDHL) (NI)\n");
                    }
                    case '4' -> {
                        if (debug) log("DEC double-height line, bottom half (DECDHL) (NI)\n");
                    }
                    case '5' -> {
                        if (debug) log("DEC single-width line (DECSWL) (NI)\n");
                    }
                    case '6' -> {
                        if (debug) log("DEC double-width line (DECDWL) (NI)\n");
                    }
                    case '8' -> {
                        if (debug) log("DEC Screen Alignment Test (DECALN) (NI)\n");
                    }
                    default -> {
                        if (debug) log("Unknown  (NI)\n");
                    }
                }
            }
            case '%' -> {
                if (debug) log(" todo\n");
            }
            case '(' -> {
                if (debug) log("Designate G0 Character Set (ISO 2022) " + ((char) second) + " (NI)\n");
            }
            case ')' -> {
                if (debug) log("Designate G1 Character Set (ISO 2022) " + ((char) second) + " (NI)\n");
            }
            case '*' -> {
                if (debug) log("Designate G2 Character Set (ISO 2022) " + ((char) second) + " (NI)\n");
            }
            case '+' -> {
                if (debug) log("Designate G3 Character Set (ISO 2022) " + ((char) second) + " (NI)\n");
            }

            case '7' -> // Save Cursor (DECSC)
            // things saved & restored here is defined by DEC:
            // https://vt100.net/docs/vt510-rm/DECSC.html
            // - Cursor position
            // - Character attributes set by the SGR command
            // - Character sets (G0, G1, G2, or G3) currently in GL and GR
            // - Wrap flag (autowrap or no autowrap)
            // - State of origin mode (DECOM)
            // - Selective erase attribute
            // - Any single shift 2 (SS2) or single shift 3 (SS3) functions sent
            //
            {
                if (debug) log("Save Cursor (NI)\n");
            }
            case '8' -> //Restore Cursor (DECRC)
            {
                if (debug) log("Restore Cursor (NI)\n");
            }
            case '=' -> // Application Keypad (DECPAM)
            {
                if (debug) log("Application Keypad (NI)\n");
            }
            case '>' -> {
                if (debug) log("Normal Keypad (DECPNM) (NI)\n");
            }
            case 'D' -> {
                // IND Index
                if (debug) log("Move the cursor one line down scrolling if needed\n");
                pane.moveCaret(0, 1);
            }
            case 'E' -> {
                // NEL	Next Line
                if (debug) log("Move the cursor to the beginning of the next row\n");
                pane.setCaretAbsolute(0, pane.getCaretY() + 1);
            }
            case 'F' -> {
                if (debug) log("Cursor to lower left corner of screen (disabled)\n");
            }
            case 'H' -> // HTS Horizontal Tabulation Set
            {
                if (debug) log("Places a tab stop at the current cursor position (NI)\n");
            }
            case 'M' -> { // IR	Reverse Index
                if (debug) log("Move the cursor one line up scrolling if needed\n");
                pane.moveCaret(0, -1);
            }
            case 'c' -> {
                if (debug) log("Full Reset (RIS) (NI)\n");
            }
            case 'l' -> {
                if (debug) log("Locks memory above the cursor (HP terminals) (NI)\n");
            }
            case 'm' -> {
                if (debug) log("Memory Unlock (HP terminals) (NI)\n");
            }
            case 'n' -> {
                if (debug) log("Invoke the G2 Character Set as GL (LS2) (NI)\n");
            }
            case 'o' -> {
                if (debug) log("Invoke the G3 Character Set as GL (LS3) (NI)\n");
            }
            case '|' -> {
                if (debug) log("Invoke the G3 Character Set as GR (LS3R) (NI)\n");
            }
            case '}' -> {
                if (debug) log("Invoke the G2 Character Set as GR (LS2R) (NI)\n");
            }
            case '~' -> {
                if (debug) log("Invoke the G1 Character Set as GR (LS1R)v");
            }
            default -> {
                if (debug) log("Unknown (NI)\n");
            }
        }
        return null;
    }

    protected byte[] handleCommand(int c) {
        String[] params = arguments.toString().split(";");

        // Nope
        return switch (type) {
            case csi -> handleCsiCommand(c, params);
            case osc -> handleOscCommand(c, params);
            case pm ->
                // Nope
                    null;
            case escSingleChar -> handleEscCommand(infix, c);
        };
    }

    /**
     * Type of control sequence.
     */
    protected enum Type {
        // Control Sequence Intro
        csi,
        // Operating System Command
        osc,
        // Private Message (TODO: do we need this?)
        pm,
        escSingleChar
    }

    /**
     * Parser State
     */
    protected enum State {
        normal,
        esc,
        // type indicator
        typeIndicator,
        arguments,
        escSingleChar
    }

}
