package com.bw.sshTerm;

import java.awt.event.KeyEvent;
import java.nio.charset.StandardCharsets;

public class Xterm {

    public State state = State.normal;
    public Type type = null;
    public StringBuilder arguments = new StringBuilder();

    byte infix = 0;
    boolean bracketedPasteMode = false;
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
                        case KeyEvent.VK_ENTER -> "\015";
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
            pane.setCaretAbsolute(0, pane.caretY + 1);
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
                    case 13 -> pane.setCaretAbsolute(0, pane.caretY);
                    case 8 -> {
                        System.out.println("BS ");
                        pane.moveCaret(-1, 0);
                    }
                    case 10 -> {
                        pane.setCaretAbsolute(0, pane.caretY + 1);
                    }
                    default -> {
                        addChar((char) c, pane);
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
                        handleEscCommand(c, (byte) 0, pane);
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
                        addChar('\033', pane);
                        addChar((char) c, pane);
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
                    handleCommand(c, pane);
                    state = State.normal;
                }
            }
            case escSingleChar -> {
                // Esc with one single character. Firsat char in infix
                handleCommand(c, pane);
                state = State.normal;
            }
            case arguments -> {
                if ((c >= '0' && c <= '9') || c == ';' ||
                        (type == Type.osc && c >= 32 && c <= 126)) {
                    arguments.append((char) c);
                } else {
                    handleCommand(c, pane);
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

    protected String getTextParameter(int n, String defaultVal, String[] params) {
        return params.length > n && !params[n].isEmpty() ? params[n] : defaultVal;
    }

    public void handleCsiCommand(byte c, String[] params, TerminalPane pane) {
        System.out.println("Command CSI " + (infix == 0 ? "" : "" + (char) infix) + (c >= 32 ? "'" + ((char) c) + "'" : String.valueOf(c)) + " {" + String.join(",", params) + "}");
        switch ((char) c) {
            case 'd' -> { // VPA Move to the corresponding vertical position (line Ps) of the current column (default 1).
                int ps = getIntParameter(0, 1, params) - 1;
                System.out.println(" -> Move caret to " + pane.caretX + "," + ps);
                pane.setCaretAbsolute(pane.caretX, ps);
            }
            case 'h' -> {
                if (infix == '?') {
                    // DEC Private Mode Set (DECSET)
                    for (String p : params) {
                        int ps = Integer.parseInt(p);

                        switch (ps) {
                            case 1 -> { //  Application Cursor Keys (DECCKM)
                            }
                            case 2 -> { //  Designate USASCII for character sets G0-G3 (DECANM), and set VT100 mode.
                            }
                            case 3 -> { //  132 Column Mode (DECCOLM)
                            }
                            case 4 -> { //  Smooth (Slow) Scroll (DECSCLM)
                            }
                            case 5 -> { //  Reverse Video (DECSCNM)
                            }
                            case 6 -> { //  Origin Mode (DECOM)
                            }
                            case 7 -> { //  Wraparound Mode (DECAWM)
                            }
                            case 8 -> { //  Auto-repeat Keys (DECARM)
                            }
                            case 9 -> { //  Send Mouse X & Y on button press. See the section Mouse Tracking.
                            }
                            case 10 -> { //  Show toolbar (rxvt)
                            }
                            case 12 -> { //  Start Blinking Cursor (att610)
                                System.out.println(" -> Start Blinking Cursor (not yet)");
                            }
                            case 18 -> { //  Print form feed (DECPFF)
                            }
                            case 19 -> { //  Set print extent to full screen (DECPEX)
                            }
                            case 25 -> { //  Show Cursor (DECTCEM)
                                System.out.println(" -> Show Cursor");
                                pane.showCursor = true;
                            }
                            case 30 -> { //  Show scrollbar (rxvt).
                            }
                            case 35 -> { //  Enable font-shifting functions (rxvt).
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
                            }
                            case 47 -> { //  Use Alternate Screen Buffer (unless disabled by the titeInhibit resource)
                            }
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
                            case 1047 -> { // Use Alternate Screen Buffer (unless disabled by the titeInhibit resource)
                            }
                            case 1048 -> { // Save cursor as in DECSC (unless disabled by the titeInhibit resource)
                            }
                            case 1049 -> {
                                // Save cursor as in DECSC and use Alternate Screen Buffer,
                                // clearing it first (unless disabled by the titeInhibit resource).
                                // This combines the effects of the 1047 and 1048 modes.
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
                                bracketedPasteMode = true;
                            }
                        }
                    }

                } else {

                }
            }
            case 'l' -> {
                if (infix == '?') {
                    // DEC Private Mode Reset (DECRST)
                    for (String p : params) {
                        int ps = Integer.parseInt(p);
                        switch (ps) {
                            case 1 -> { // Normal Cursor Keys (DECCKM).
                            }
                            case 2 -> { // Designate VT52 mode (DECANM).
                            }
                            case 3 -> { // 80 Column Mode (DECCOLM).
                            }
                            case 6 -> { // Normal Cursor Mode (DECOM).
                            }
                            case 7 -> { // No Wraparound Mode (DECAWM).
                            }
                            case 8 -> { // No Auto-repeat Keys (DECARM).
                            }
                            case 9 -> { // Don’t send Mouse X & Y on button press.
                            }
                            case 12 -> { // Stop Blinking Cursor.
                                System.out.println(" -> Stop Blinking Cursor");
                            }
                            case 25 -> { // Hide Cursor (DECTCEM).
                                System.out.println(" -> Hide Cursor");
                                pane.showCursor = false;
                            }
                            case 45 -> { // No reverse wrap-around.
                            }
                            case 47 -> { // Use Normal Screen Buffer.
                            }
                            case 66 -> { // Numeric keypad (DECNKM).
                            }
                            case 1000 -> { // Don’t send Mouse reports.
                            }
                            case 1002 -> { // Don’t use Cell Motion Mouse Tracking.
                            }
                            case 1003 -> { // Don’t use All Motion Mouse Tracking.
                            }
                            case 1004 -> { // Don’t send FocusIn/FocusOut events.
                            }
                            case 1005 -> { // Disable UTF-8 Mouse Mode.
                            }
                            case 1006 -> { // Disable SGR Mouse Mode.
                            }
                            case 1015 -> { // Disable urxvt Mouse Mode.
                            }
                            case 1016 -> { // Disable SGR-Pixels Mouse Mode.
                            }
                            case 1047 -> { // Use Normal Screen Buffer (clearing screen if in alt).
                            }
                            case 1048 -> { // Restore cursor as in DECRC.
                            }
                            case 1049 -> { // Use Normal Screen Buffer and restore cursor.
                            }
                            case 2004 -> { // Reset bracketed paste mode.
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
                            case 12 -> // 	Send/receive (SRM).
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
                System.out.println(" -> Select character attributes. The default value of Pm is 0.");
                for (String param : params) {
                    int code = param.isEmpty() ? 0 : Integer.parseInt(param);
                    applySgrCode(code);
                }
            }
            case 'r' // DECSTBM
                    -> {
                System.out.println(" -> Set top and bottom margins");
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
                        case 20 -> { // Report xterm window’s icon label as OSC L label S} ] case 21 -> { // Report xterm window’s title as OSC l title S}
                        }
                        case 22 -> { //   Save window title on stack.
                            System.out.println(" -> Save window title on stack.");
                            // Ps2 = 0, 1, 2    Save window title.
                        }
                        case 23 -> { //     Restore window title from stack.
                            // Ps2 = 0, 1, 2    Restore window title.
                        }
                        default -> {
                            // >= 2 4 → Resize to P s lines (DECSLPP)
                        }
                    }
                }

            }
            case 'A' // CUU - Cursor Up
                    -> {
                System.out.println(" -> Cursor Up");
                pane.setCaretAbsolute(pane.caretX, pane.caretY - 1);
            }
            case 'B' // CUD - Cursor Down
                    -> {
            }
            case 'C' -> pane.moveCaret(1, 0);  // CUF - Cursor Forward
            case 'D' -> pane.moveCaret(-1, 0); // CUB - Cursor Backward
            case 'G' -> {
                // Cursor Character Absolute [column] (default = [row,1]) (CHA)
                // Moves cursor to the Ps-th column of the active line. The default value of Ps is 1.
                int ps = getIntParameter(0, 1, params) - 1;
                System.out.println(" -> Move caret to " + ps + "," + pane.caretY);
                pane.setCaretAbsolute(ps, pane.caretY);
            }
            case 'f', // HVP - Horizontal and Vertical Position (identisch zu CUP)
                 'H' -> // CUP - sCursor Position
            {
                // Moves cursor to the Ps1-th line and to the Ps2-th column. The default value of Ps1 and Ps2 is 1.
                int row = getIntParameter(0, 1, params) - 1;
                int col = getIntParameter(1, 1, params) - 1;
                System.out.println(" -> Move caret to " + col + "," + row);
                pane.setCaretAbsolute(col, row);
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
    }

    public void handleOscCommand(byte c, String[] params, TerminalPane pane) {
        System.out.println("Command OSC " + (infix == 0 ? "" : "" + (char) infix) + (c >= 32 ? "'" + ((char) c) + "'" : String.valueOf(c)) + " {" + String.join(",", params) + "}");
        switch (c) {
            case 7, (byte) 0x9C -> // BELL or ST: Set Text Parameters
            {
                int ps = getIntParameter(0, 0, params);
                String pt = getTextParameter(1, "", params);

                switch (ps) {
                    case 0 -> // Change Icon Name and Window Title to pt
                    {
                        pane.setTitle(pt);
                    }
                    case 1 -> { // Change Icon Name to pt

                    }
                    case 2 -> { // Change Window Title to pt

                    }
                    case 3 -> {
                        // Set X property on top-level window.
                        // Pt should be in the form "prop=value", or just "prop" to delete the property
                    }
                    case 4 -> {
                        // pt=c;spec;... Change color number c to the color specified by spec.
                        // The color numbers correspond to the
                        // + ANSI colors 0-7,
                        // + their bright versions 8-15, and if supported,
                        // + the remainder of the 88-color or 256-color table.
                        // TODO: For "?" as spec, a response is needed
                    }
                    case 10, 11, 12, 13, 14, 15, 16, 17, 18 -> {
                        // Dynamic colors
                    }
                    case 46 -> {
                        // Change Log File to pr (disabled)
                    }
                    case 50 -> {
                        // Set Font to pt - nope!
                    }
                    case 52 -> {
                        // Manipulate Selection Data (disabled)
                    }
                }
            }
        }
    }

    /**
     * Simple 2 char commands
     */
    public void handleEscCommand(byte first, byte second, TerminalPane pane) {
        System.out.print("Command ESC" + ((char) first) + "" + (second == 0 ? "" : (char) second) + " -> ");

        switch (first) {
            case ' ' -> {
                switch (second) {
                    case 'F' -> System.out.println("7-bit controls (S7C1T)");
                    case 'H' -> System.out.println("8-bit controls (S8C1T)");
                    case 'L' -> System.out.println("Set ANSI conformance level 1 (dpANS X3.134.1)");
                    case 'M' -> System.out.println("Set ANSI conformance level 2 (dpANS X3.134.1)");
                    case 'N' -> System.out.println("Set ANSI conformance level 3 (dpANS X3.134.1)");
                    default -> System.out.println("Unknown");
                }

            }
            case '#' -> {
                switch (second) {
                    case '3' -> System.out.println("DEC double-height line, top half (DECDHL)");
                    case '4' -> System.out.println("DEC double-height line, bottom half (DECDHL)");
                    case '5' -> System.out.println("DEC single-width line (DECSWL)");
                    case '6' -> System.out.println("DEC double-width line (DECDWL)");
                    case '8' -> System.out.println("DEC Screen Alignment Test (DECALN)");
                    default -> System.out.println("Unknown");
                }
            }
            case '%' -> {
                System.out.println(" todo");
            }
            case '(' -> System.out.println("Designate G0 Character Set (ISO 2022) " + second);
            case ')' -> System.out.println("Designate G1 Character Set (ISO 2022) " + second);
            case '*' -> System.out.println("Designate G2 Character Set (ISO 2022) " + second);
            case '+' -> System.out.println("Designate G3 Character Set (ISO 2022) " + second);

            case '7' -> {
                // Save Cursor (DECSC)
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
                System.out.println("Save Cursor");
            }
            case '8' -> {
                //Restore Cursor (DECRC)
                System.out.println("Restore Cursor");
            }
            case '=' -> {
                // Application Keypad (DECPAM)
                System.out.println("Application Keypad");
            }
            case '>' -> {
                System.out.println("Normal Keypad (DECPNM)");
            }
            case 'D' -> {
                // IND Index
                System.out.println("Move the cursor one line down scrolling if needed.");
                pane.moveCaret(0, 1);
            }
            case 'E' -> {
                // NEL	Next Line
                System.out.println("Move the cursor to the beginning of the next row");
                pane.setCaretAbsolute(0, pane.caretY+1);
            }
            case 'F' -> {
                System.out.println("Cursor to lower left corner of screen (disabled)");
            }
            case 'H' -> {
                // HTS Horizontal Tabulation Set
                System.out.println("Places a tab stop at the current cursor position");
            }
            case 'M' -> { // IR	Reverse Index
                System.out.println("Move the cursor one line up scrolling if needed.");
                pane.moveCaret(0, -1);
            }
            case 'c' -> {
                System.out.println("Full Reset (RIS)");
            }
            case 'l' -> {
                System.out.println("Locks memory above the cursor (HP terminals");
            }
            case 'm' -> {
                System.out.println("Memory Unlock (HP terminals)");
            }
            case 'n' -> {
                System.out.println("Invoke the G2 Character Set as GL (LS2)");
            }
            case 'o' -> {
                System.out.println("Invoke the G3 Character Set as GL (LS3)");
            }
            case '|' -> {
                System.out.println("Invoke the G3 Character Set as GR (LS3R).");
            }
            case '}' -> {
                System.out.println("Invoke the G2 Character Set as GR (LS2R)");
            }
            case '~' -> {
                System.out.println("Invoke the G1 Character Set as GR (LS1R)");
            }
            default -> System.out.println("Unknown");
        }
    }

    public void handleCommand(byte c, TerminalPane pane) {
        String r = null;
        String[] params = arguments.toString().split(";");

        switch (type) {
            case csi -> handleCsiCommand(c, params, pane);
            case osc -> handleOscCommand(c, params, pane);
            case pm -> {
                // Nope
            }
            case escSingleChar -> {
                handleEscCommand(infix, c, pane);
            }
        }
    }

    public enum Type {
        // Control Sequence Intro
        csi,
        // Operating System Command
        osc,
        // Private Message (TODO: do we need this?)
        pm,
        escSingleChar;
    }

    public enum State {
        normal,
        esc,
        // type indicator
        typeIndicator,
        arguments,
        escSingleChar
    }

}
