/**
 * Copyright 2025, Bernd Wengenroth.
 * This is free and unencumbered software released into the public domain.
 * Check LICENSE for details.
 */
package com.bw.sshTerm;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * A panel to show a terminal.
 */
public class TerminalPane extends JComponent {

    /**
     * Property for the effective terminal size. Value is an int[4]:<ul>
     * <li>termWidth,
     * <li>termHeight,
     * <li>charWidth,
     * <li>charHeight
     * </ul>
     */
    public final static String PROPERTY_TERM_SIZE = "termSize";

    ;
    private final Map<Integer, Screen> screens = new HashMap<>();
    public boolean showCursor = true;
    public Color background;
    public Color foreground;
    public boolean connected;
    protected int charWidth;
    protected int charHeight;
    protected int termWidth;
    protected int termHeight;

    protected XC[][] lines = new XC[0][0];
    protected int ascent;
    int caretY = 0;
    int caretX = 0;
    private Screen activeScreenBuffer = new Screen();
    private int activeScreen = 0;
    private String title = null;

    public TerminalPane() {
        super();
        enableEvents(AWTEvent.KEY_EVENT_MASK | AWTEvent.INPUT_METHOD_EVENT_MASK);

        setLayout(null);
        setFont(new Font("Monospaced", Font.PLAIN, 14));
        setFocusable(true);
        setRequestFocusEnabled(true);
        setEnabled(true);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                requestFocusInWindow();
            }
        });


        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                super.focusGained(e);
                System.out.println("FOCUS");
            }
        });
    }

    /**
     * Get the id of the active screen.
     *
     * @return The id of the active screen.
     */
    public int getActiveScreen() {
        return activeScreen;
    }

    /**
     * Switch active screen. A new screen is created if needed.
     *
     * @param id The id of the active screen. Can be any integer.
     */
    public void switchScreen(int id, boolean restoreCaret) {
        if (activeScreen != id) {
            activeScreen = id;
            activeScreenBuffer.lastCaretX = caretX;
            activeScreenBuffer.lastCaretY = caretY;

            activeScreenBuffer = screens.get(id);
            if (activeScreenBuffer == null) {
                activeScreenBuffer = new Screen();
                screens.put(id, activeScreenBuffer);
            }
            if (restoreCaret) {
                caretX = activeScreenBuffer.lastCaretX;
                caretY = activeScreenBuffer.lastCaretY;
            }
            System.out.println("Switched to screen " + id);
            repaint();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        updateTerminalSpecs();
        return new Dimension(charWidth * termWidth, charHeight * Math.max(activeScreenBuffer.term.size(), termHeight));
    }

    /**
     * Moved the caret relative. Scrolls if the margin reached.
     *
     * @param xd The delta in x-direction.
     * @param yd The delta in y-direction.
     */
    public void moveCaret(int xd, int yd) {
        caretX += xd;
        caretY += yd;
        while (caretY > activeScreenBuffer.marginBottom) {
            activeScreenBuffer.scrollDown();
            caretY--;
        }
        while (caretY < activeScreenBuffer.marginTop) {
            activeScreenBuffer.scrollUp();
            caretY++;
        }
        repaint();
    }

    /**
     * Sets the caret. Scrolls if the margin reached.
     *
     * @param x The x-ordinate.
     * @param y The y-ordinate.
     */
    public void setCaretAbsolute(int x, int y) {
        if (activeScreenBuffer.marginBottom == 0) activeScreenBuffer.marginBottom = termHeight - 1;
        System.out.println("setCarAbs " + x + "," + y + " [" + activeScreenBuffer.marginTop + "," + activeScreenBuffer.marginBottom + "]");
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        caretX = x;
        caretY = y;
        while (caretY > activeScreenBuffer.marginBottom) {
            caretY--;
            activeScreenBuffer.scrollDown();
        }
        while (caretY < activeScreenBuffer.marginTop) {
            caretY++;
            activeScreenBuffer.scrollUp();
        }
        repaint();
    }

    /**
     * Get the x-ordinate of the caret.
     */
    public int getCaretX() {
        return caretX;
    }

    /**
     * Get the y-ordinate of the caret.
     */
    public int getCaretY() {
        return caretY;
    }

    /**
     * Set char
     *
     * @param x The column in terminal. [0 - termWidth[
     * @param y The row in terminal. [0 - termHeight[
     * @param b The character to set
     */
    public void setCharAt(int x, int y, char b) {
        activeScreenBuffer.setCharAt(x, y, b);
        repaint();
    }

    /**
     * Set char at a current caret (doesn't move the caret)
     *
     * @param b The character to set
     */
    public void setChar(char b) {
        setCharAt(caretX, caretY, b);
    }

    @Override
    public void paintComponent(Graphics g) {
        int x = charWidth * 3;
        int y = ascent;
        int by = 0;
        Graphics2D g2 = (Graphics2D) g.create();
        char[] cc = {0};
        try {
            if (connected) {

                synchronized (activeScreenBuffer.term) {
                    lines = activeScreenBuffer.term.toArray(lines);
                }
                final Color background = getBackground();
                final Color foreground = getForeground();

                Color currentColor = Color.RED;
                g2.setPaint(Color.RED);

                for (int i = 1; i <= lines.length; ++i) {
                    g2.drawString(String.format("%03d", i), 0, y);
                    y += charHeight;
                }
                y = ascent;

                for (XC[] line : lines) {
                    if (line == null) {
                        break;
                    }
                    int cx = x;
                    for (XC c : line) {
                        if (c == null)
                            break;

                        Color BG = c.background == null ? background : c.background;
                        if (BG != background) {
                            if (BG != currentColor) {
                                g2.setPaint(BG);
                                currentColor = BG;
                            }
                            g2.fillRect(cx, by, charWidth, charHeight);
                        }
                        if (c.c != 0) {
                            cc[0] = c.c;
                            Color FB = c.color == null ? foreground : c.color;
                            if (FB != currentColor) {
                                g2.setPaint(FB);
                                currentColor = FB;
                            }
                            g2.drawChars(cc, 0, 1, cx, y);
                        }
                        cx += charWidth;
                    }
                    y += charHeight;
                    by += charHeight;
                }
                if (showCursor)
                    g2.drawRect(x + caretX * charWidth, caretY * charHeight, charWidth, charHeight);
            } else {
                Dimension d = getSize();
                String text = "Connecting...";
                FontMetrics fm = g2.getFontMetrics();
                int w = fm.stringWidth(text);
                g2.setPaint(Color.BLACK);
                g2.drawString(text, (d.width - w) / 2, (d.height / 2) - fm.getAscent());
            }
        } finally {
            g2.dispose();
        }
    }

    public int[] getTermSizes() {
        return new int[]{termWidth, termHeight, charWidth, charHeight};
    }

    protected void updateTerminalSpecs() {
        FontMetrics metrics = getFontMetrics(getFont());
        int[] terminalSpec = new int[]{termWidth, termHeight, charWidth, charHeight};

        int newCharWidth = metrics.charWidth('W'); // Breite eines Zeichens
        int newCharHeight = metrics.getHeight();   // Höhe eines Zeichens

        Dimension d = getSize();
        System.out.println("SIZE " + d);

        int newTermWitdh = d.width / newCharWidth;
        int newTermHeight = d.height / newCharHeight;

        if (newTermWitdh < 20)
            newTermWitdh = 20;
        if (newTermHeight < 20)
            newTermHeight = 20;

        if (newTermHeight > 0 && newTermWitdh > 0 &&
                (charHeight != newCharHeight ||
                        charWidth != newCharWidth ||
                        termWidth != newTermWitdh ||
                        termHeight != newTermHeight)) {

            charHeight = newCharHeight;
            charWidth = newCharWidth;
            ascent = metrics.getAscent();
            termWidth = newTermWitdh;

            if (activeScreenBuffer.marginBottom == (termHeight - 1)) {
                activeScreenBuffer.setMargins(activeScreenBuffer.marginTop, newTermHeight - 1);
            }
            termHeight = newTermHeight;
            activeScreenBuffer.ensureSpace();

            System.out.printf("\nNew Terminal Dimension: %d x %d. char %d x %d\n", termWidth, termHeight, charWidth, charHeight);

            int[] newTerminalSpec = new int[]{termWidth, termHeight, charWidth, charHeight};
            firePropertyChange(PROPERTY_TERM_SIZE, terminalSpec, newTerminalSpec);
        }
    }

    public void clear() {
        activeScreenBuffer.clear();
        caretX = 0;
        caretY = 0;

        repaint();
    }

    public void deleteChar() {
        XC[] line = activeScreenBuffer.term.get(caretY);
        for (int x = caretX; x < (line.length - 1); ++x) {
            line[x] = line[x + 1];
        }
        line[line.length - 1] = null;
        repaint();
    }

    public void insert(char c) {
        XC[] line = activeScreenBuffer.term.get(caretY);
        if (line.length <= caretX) {
            line = Arrays.copyOf(line, caretX + 10);
            activeScreenBuffer.term.set(caretY, line);
        }
        for (int x = (line.length - 1); x > caretX; --x) {
            line[x] = line[x - 1];
        }
        XC xc = line[caretX];
        if (xc == null)
            line[caretX] = xc = new XC();
        xc.c = c;
        xc.color = foreground;
        xc.background = background;
        repaint();
    }

    public void setTitle(String text) {
        if (!Objects.equals(text, this.title)) {
            this.title = text;
            SwingUtilities.invokeLater(() -> {
                if (SwingUtilities.getWindowAncestor(this) instanceof Frame f) {
                    f.setTitle(this.title);
                }
            });
        }
    }

    public void setMargins(int top, int bottom) {
        activeScreenBuffer.setMargins(top, bottom);
        repaint();
    }

    public Color getCharForeground() {
        return foreground == null ? getForeground() : foreground;
    }

    public void setCharForeground(Color fg) {
        foreground = fg;
    }

    public Color getCharBackground() {
        return background == null ? getBackground() : background;
    }

    public void setCharBackground(Color bg) {
        background = bg;
    }

    /**
     * Get the system clip-board content.
     */
    public String getClipboardContents() {
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

    static final class XC {
        Color color;
        Color background;
        char c;
    }

    class Screen {
        /**
         * Zero based upper margin in range [0, termHeight-1[
         */
        public int marginTop = 0;

        /**
         * Zero based lower margin in range [1, termHeight[
         */
        public int marginBottom = 0;

        public int lastCaretX;
        public int lastCaretY;

        public List<XC[]> topScrollBuffer = new ArrayList<>(100);
        public List<XC[]> bottomScrollBuffer = new ArrayList<>(100);

        public List<XC[]> term = new ArrayList<>(100);

        public void setMargins(int top, int bottom) {
            System.out.println("setMargins(" + marginTop + "," + marginBottom + ") -> (" + top + "," + bottom + ")");
            while (marginTop > top) {
                marginTop--;
            }
            while (marginTop < top) {
                marginTop++;
            }
            while (marginBottom > bottom) {
                marginBottom--;
            }
            while (marginBottom < bottom) {
                marginBottom++;
                while (term.size() < (marginBottom - marginTop))
                    term.add(new XC[termWidth]);
            }
        }

        protected void ensureSpace() {
            while (term.size() > termHeight)
                term.remove(term.size() - 1);
            while (term.size() < termHeight) {
                term.add(new XC[termWidth]);
            }
        }

        public void clear() {
            topScrollBuffer.clear();
            bottomScrollBuffer.clear();
            term.clear();
            setMargins(0, termHeight - 1);
            ensureSpace();
        }

        public void scrollDown() {

            topScrollBuffer.add(term.remove(marginTop));
            if (bottomScrollBuffer.isEmpty())
                term.add(marginBottom, new XC[termWidth]);
            else
                term.add(marginBottom, bottomScrollBuffer.remove(bottomScrollBuffer.size() - 1));
            System.out.println("After ScrollDown: [" + marginTop + "," + marginBottom + "] term:" + term.size());
        }

        public void scrollUp() {
            bottomScrollBuffer.add(term.remove(marginBottom));
            term.add(marginTop, topScrollBuffer.remove(topScrollBuffer.size() - 1));
            System.out.println("After ScrollUp: [" + marginTop + "," + marginBottom + "] term:" + term.size());
        }

        public void setCharAt(int x, int y, char b) {
            try {
                System.out.println("char (" + x + "," + y + ")=" + (b < ' ' ? "Ox" + Integer.toHexString(b) : "" + b));
                if (y >= 0) {
                    while (y > marginBottom) {
                        scrollDown();
                        --y;
                    }
                    while (term.size() <= y) {
                        term.add(new XC[termWidth]);
                    }
                    XC[] l = term.get(y);
                    if (l.length <= x) {
                        l = Arrays.copyOf(l, x + 10);
                        term.set(y, l);
                    }
                    XC xc = l[x];
                    if (xc == null) {
                        xc = new XC();
                        l[x] = xc;
                    }
                    xc.c = b;
                    xc.color = foreground;
                    xc.background = background;
                    --x;
                    while (x >= 0 && l[x] == null) {
                        l[x--] = new XC();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
