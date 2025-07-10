/**
 * Copyright 2025, Bernd Wengenroth.
 * This is free and unencumbered software released into the public domain.
 * Check LICENSE for details.
 */
package com.bw.sshTerm;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;


/**
 * A panel to show a terminal.<p>
 * The panel itself has no scrollbar. A vertical scrollbar can be bound to scroll across the scrollback-buffer by {@link #setScrollbar}.
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

    protected final Caret caret = new Caret();
    private final Map<Integer, Screen> screens = new HashMap<>();
    public boolean showCursor = true;
    public Color background;
    public Color foreground;
    public int charStyle;
    protected boolean connected;
    protected int charWidth;
    protected int charHeight;
    protected int termWidth;
    protected int termHeight;
    protected boolean caretEnabled = true;

    protected String connectMessage = "Connecting...";
    protected int ascent;
    Rectangle repaintArea = null;
    private final List<XC[]> lines = new ArrayList<>();
    private Screen activeScreenBuffer = new Screen();
    private int activeScreen = 0;
    private String title = null;
    private boolean repaintPending = false;
    private final ChangeListener scrollbarChangeListerer = e -> {
        if (activeScreenBuffer != null) {
            triggerRepaint();
        }
    };
    private JScrollBar scrollbar;
    private int baseY;
    private Map<RenderingHints.Key, Object> hints;


    public TerminalPane() {
        this("Monospaced-PLAIN-14");
    }

    /**
     * Initialize the terminal pane.
     *
     * @param fontDescription The font description, as described in {@link Font#decode(String)}
     */
    public TerminalPane(String fontDescription) {

        super();
        enableEvents(AWTEvent.KEY_EVENT_MASK | AWTEvent.INPUT_METHOD_EVENT_MASK);

        setLayout(null);
        setFont(Font.decode(fontDescription));
        setFocusable(true);
        setRequestFocusEnabled(true);
        setEnabled(true);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                requestFocusInWindow();
            }
        });

        caret.install(this);

        addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                caret.setVisible(caretEnabled);
            }

            @Override
            public void focusLost(FocusEvent e) {
                caret.setVisible(false);
            }
        });
    }

    /**
     * Helper to get the system clip-board content.
     */
    public static String getClipboardContents() {
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

    public void addRenderingHint(RenderingHints.Key key, Object value) {
        if (hints == null)
            hints = new HashMap<>();
        hints.put(key, value);
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
            activeScreenBuffer.lastCaretX = caret.getCaretX();
            activeScreenBuffer.lastCaretY = caret.getCaretY();

            activeScreenBuffer = screens.get(id);
            if (activeScreenBuffer == null) {
                activeScreenBuffer = new Screen();
                screens.put(id, activeScreenBuffer);
            }
            if (restoreCaret) {
                caret.setCaret(activeScreenBuffer.lastCaretX, activeScreenBuffer.lastCaretY);
            }
            System.out.println("Switched to screen " + id);
            activeScreenBuffer.repaint = true;

            if (scrollbar != null)
                scrollbar.setEnabled(activeScreen == 0);
            configureScrollbar();
            triggerRepaint();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        updateTerminalSpecs();
        return new Dimension(charWidth * termWidth, charHeight * Math.max(activeScreenBuffer.term.size(), termHeight));
    }

    /**
     * Moved the caret relative. Scrolls if the margin is reached.
     *
     * @param xd The delta in x-direction.
     * @param yd The delta in y-direction.
     */
    public void moveCaret(int xd, int yd) {
        caret.setCaretX(getCaretX() + xd);
        caret.setCaretY(getCaretY() + yd);
        while (caret.getCaretY() > activeScreenBuffer.marginBottom) {
            activeScreenBuffer.scrollDown();
            caret.setCaretY(caret.getCaretY() - 1);
        }
        while (caret.getCaretY() < activeScreenBuffer.marginTop) {
            activeScreenBuffer.scrollUp();
            caret.setCaretY(caret.getCaretY() + 1);
        }
        triggerRepaintCursor();
    }

    /**
     * Sets the caret. Scrolls if the margin reached.
     *
     * @param x The x-ordinate.
     * @param y The y-ordinate.
     */
    public void setCaretAbsolute(int x, int y) {
        if (activeScreenBuffer.marginBottom == 0) activeScreenBuffer.marginBottom = termHeight - 1;
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        while (y > activeScreenBuffer.marginBottom) {
            y--;
            activeScreenBuffer.scrollDown();
        }
        while (y < activeScreenBuffer.marginTop) {
            y++;
            activeScreenBuffer.scrollUp();
        }
        caret.setCaret(x, y);
        triggerRepaintCursor();
    }

    /**
     * Get the x-ordinate of the caret.
     */
    public int getCaretX() {
        return caret.getCaretX();
    }

    /**
     * Get the y-ordinate of the caret.
     */
    public int getCaretY() {
        return caret.getCaretY();
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
        triggerRepaint();
    }

    /**
     * Set char at a current caret (doesn't move the caret)
     *
     * @param b The character to set
     */
    public void setChar(char b) {
        setCharAt(caret.getCaretX(), caret.getCaretY(), b);
    }

    @Override
    public void paintComponent(Graphics g) {
        final Graphics2D g2 = (Graphics2D) g.create();

        if (hints != null)
            g2.setRenderingHints(hints);

        caret.caretIsCleared();
        repaintPending = false;
        int x = getLeftPageMargin();
        int y = ascent;
        int by = 0;
        char[] cc = {0};
        final Font normal = getFont();
        Font currentFont = normal;
        Font bold = null;
        int currentStyle = 0;
        boolean underlined = false;
        try {
            if (connected) {
                lines.clear();
                synchronized (activeScreenBuffer) {
                    if (activeScreen == 0 && scrollbar != null) {
                        int sv = scrollbar.getValue();
                        if (sv < activeScreenBuffer.topScrollBuffer.size()) {
                            lines.addAll(activeScreenBuffer.topScrollBuffer.subList(sv, activeScreenBuffer.topScrollBuffer.size() - 1));
                            baseY = charHeight * lines.size();
                        }
                    } else {
                        baseY = 0;
                    }
                    lines.addAll(activeScreenBuffer.term);
                }

                final Color background = getBackground();
                final Color foreground = getForeground();

                Color currentColor = Color.RED;
                g2.setPaint(Color.RED);

                Rectangle r2 = g2.getClip().getBounds();
                int startLine = 0;
                while ((y + charHeight) < r2.getY()) {
                    y += charHeight;
                    ++startLine;
                }

                int lastLine = startLine + 1 + ((int) (r2.getHeight() / charHeight));
                if (lastLine >= lines.size())
                    lastLine = lines.size() - 1;

                int yp = y;
                for (int i = startLine; i <= lastLine; ++i) {
                    g2.drawString(String.format("%03d", i + 1), 0, yp);
                    yp += charHeight;
                }

                for (int i = startLine; i <= lastLine; ++i) {
                    XC[] line = lines.get(i);
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
                                g2.setColor(BG);
                                currentColor = BG;
                            }
                            g2.fillRect(cx, by, charWidth, charHeight);
                        }
                        if (c.c != 0) {
                            cc[0] = c.c;
                            Color FB = c.color == null ? foreground : c.color;
                            if (FB != currentColor) {
                                g2.setColor(FB);
                                currentColor = FB;
                            }
                            if (currentStyle != c.style) {
                                currentStyle = c.style;
                                if ((currentStyle & CharStyle.BOLD) != 0) {
                                    if (bold == null)
                                        bold = currentFont.deriveFont(Font.BOLD);
                                    if (currentFont != bold) {
                                        currentFont = bold;
                                        g2.setFont(currentFont);
                                    }
                                } else if (currentFont != normal) {
                                    currentFont = normal;
                                    g2.setFont(currentFont);
                                }
                                underlined = (currentStyle & CharStyle.UNDERLINED) != 0;
                            }
                            g2.drawChars(cc, 0, 1, cx, y);
                            if (underlined) {
                                g2.drawLine(cx, y + 1, cx + charWidth - 1, y + 1);
                            }
                        }
                        cx += charWidth;
                    }
                    y += charHeight;
                    by += charHeight;
                }
                caret.drawCursor(g2);
            } else {
                Dimension d = getSize();
                FontMetrics fm = g2.getFontMetrics();
                int w = fm.stringWidth(connectMessage);
                g2.setPaint(Color.BLACK);
                g2.drawString(connectMessage, (d.width - w) / 2, (d.height / 2) - fm.getAscent());
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

        if (charHeight != newCharHeight ||
                charWidth != newCharWidth ||
                termWidth != newTermWitdh ||
                termHeight != newTermHeight) {

            charHeight = newCharHeight;
            charWidth = newCharWidth;
            ascent = metrics.getAscent();
            termWidth = newTermWitdh;

            if (activeScreenBuffer.marginBottom == (termHeight - 1)) {
                activeScreenBuffer.setMargins(activeScreenBuffer.marginTop, newTermHeight - 1);
            }
            termHeight = newTermHeight;
            activeScreenBuffer.ensureSpace();
            configureScrollbar();

            System.out.printf("\nNew Terminal Dimension: %d x %d. char %d x %d\n", termWidth, termHeight, charWidth, charHeight);

            int[] newTerminalSpec = new int[]{termWidth, termHeight, charWidth, charHeight};
            firePropertyChange(PROPERTY_TERM_SIZE, terminalSpec, newTerminalSpec);
        }
    }

    public void clear() {
        activeScreenBuffer.clear();
        caret.setCaret(0, 0);
        triggerRepaint();
    }

    public void deleteChar() {
        XC[] line = activeScreenBuffer.term.get(getCaretY());
        for (int x = getCaretX(); x < (line.length - 1); ++x) {
            line[x] = line[x + 1];
        }
        line[line.length - 1] = null;
        triggerRepaint();
    }

    public void insert(char c) {
        activeScreenBuffer.insert(getCaretX(), getCaretY(), c);
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
     * Set char style by combination of values from {@link CharStyle}.
     *
     * @param style The style, combination of values from {@link CharStyle}.
     */
    public void setCharStyle(int style) {
        charStyle = style;
    }

    public int getCharStyle() {
        return charStyle;
    }

    public void clearCharStyle(int i) {
        charStyle = (charStyle & ~i);
    }

    /**
     * Binds the scrollback-buffer to a scrollbar.
     * If scrollback-buffer is enabled, the panel will show the area according to the position of the scrollbar.
     * The scrollbar will be configured to reflect the scrollback-buffer. The scrollbar will be disabled if an alternative screen is active.
     *
     * @param scrollbar The scrollbar, can be null.
     */
    public void setScrollbar(JScrollBar scrollbar) {
        if (this.scrollbar != scrollbar) {
            if (this.scrollbar != null)
                this.scrollbar.getModel().removeChangeListener(scrollbarChangeListerer);
            this.scrollbar = scrollbar;
            if (scrollbar != null) {
                scrollbar.getModel().addChangeListener(scrollbarChangeListerer);
                scrollbar.setEnabled(activeScreen == 0);
                configureScrollbar();
            }
        }
    }

    protected void configureScrollbar() {
        if (activeScreen == 0 && scrollbar != null) {
            scrollbar.setMinimum(0);
            scrollbar.setValue(activeScreenBuffer.topScrollBuffer.size());
            scrollbar.setBlockIncrement(termHeight);
            scrollbar.setVisibleAmount(termHeight);
            scrollbar.setMaximum(activeScreenBuffer.bottomScrollBuffer.size() + activeScreenBuffer.topScrollBuffer.size() + termHeight);
            System.out.println("Scrollbar: " + scrollbar.getValue() + " [" + scrollbar.getMinimum() + "," + scrollbar.getMaximum() + "] va " + scrollbar.getVisibleAmount());
        }

    }

    public void setConnected(boolean connected, String message) {
        this.connected = connected;
        if (message != null)
            this.connectMessage = message;
        repaint();
    }

    protected void updateCursor() {
        System.out.println("update cursor");
        Graphics2D g2 = (Graphics2D) getGraphics().create();
        try {
            caret.resetBlinking();
            caret.drawCursor(g2);
        } finally {
            g2.dispose();
        }
    }

    protected void triggerRepaint() {
        triggerRepaint(null);
    }

    protected void triggerRepaint(Rectangle area) {
        if (area == null) {
            repaintArea = new Rectangle(0, 0, getWidth(), getHeight());
        } else {
            if (repaintArea != null)
                repaintArea.add(area);
            else
                repaintArea = area;
        }
        if (repaintArea != null || activeScreenBuffer.repaint) {
            activeScreenBuffer.repaint = false;
            if (!repaintPending) {
                repaintPending = true;
                SwingUtilities.invokeLater(() -> {
                    repaintPending = false;
                    Rectangle r = repaintArea;
                    repaintArea = null;
                    if (r == null)
                        repaint();
                    else
                        repaint(r);
                });
            }
        }
    }

    protected void triggerRepaintCursor() {
        // Check if we need a full repaint.
        triggerRepaint(repaintArea);
        // If not, update cursor manually.
        if (!repaintPending) {
            SwingUtilities.invokeLater(this::updateCursor);
        }
    }

    public int getLeftPageMargin() {
        return charWidth * 4;
    }

    /**
     * Get Y - position where the first terminal line is shown.
     */
    public int getBaseY() {
        return baseY;
    }

    static final class XC {
        Color color;
        Color background;
        int style;
        char c;
    }

    class Screen {

        /**
         * Zero based upper margin in range [0, termHeight-1[
         */
        public int marginTop = 0;
        public boolean repaint = false;
        /**
         * Zero based lower margin in range [1, termHeight[
         */
        public int marginBottom = 0;
        public int lastCaretX;
        public int lastCaretY;
        public List<XC[]> topScrollBuffer = new ArrayList<>(100);
        public List<XC[]> bottomScrollBuffer = new ArrayList<>(100);
        public List<XC[]> term = new ArrayList<>(100);

        public void insert(int x, int y, char c) {
            XC[] line = term.get(y);
            if (line.length <= x) {
                line = Arrays.copyOf(line, x + 10);
                term.set(y, line);
            }
            for (int xp = (line.length - 1); xp > x; --xp) {
                line[xp] = line[xp - 1];
            }
            XC xc = line[x];
            if (xc == null)
                line[x] = xc = new XC();
            xc.c = c;
            xc.color = foreground;
            xc.background = background;
            xc.style = charStyle;
            repaint = true;
        }

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
            configureScrollbar();
        }

        public void clear() {
            topScrollBuffer.clear();
            bottomScrollBuffer.clear();
            term.clear();
            setMargins(0, termHeight - 1);
            ensureSpace();
            configureScrollbar();
            repaint = true;
        }

        public void scrollDown() {

            topScrollBuffer.add(term.remove(marginTop));
            if (bottomScrollBuffer.isEmpty())
                term.add(marginBottom, new XC[termWidth]);
            else
                term.add(marginBottom, bottomScrollBuffer.remove(bottomScrollBuffer.size() - 1));
            configureScrollbar();
            repaint = true;
        }

        public void scrollUp() {
            bottomScrollBuffer.add(term.remove(marginBottom));
            XC[] top = topScrollBuffer.isEmpty() ? new XC[0] : topScrollBuffer.remove(topScrollBuffer.size() - 1);
            term.add(marginTop, top);
            configureScrollbar();
            repaint = true;
            System.out.println("After ScrollUp: [" + marginTop + "," + marginBottom + "] term:" + term.size());
        }

        /**
         * Sets a character with current attributes at the zero based coordinates.<br>
         * Scrolls, if the position is outside the margin.
         */
        public void setCharAt(int x, int y, char b) {
            try {
                // System.out.println("char (" + x + "," + y + ")=" + (b < ' ' ? "Ox" + Integer.toHexString(b) : "" + b));
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
                    // Simplify by instance-compare.
                    if (xc.c != b || xc.color != foreground || xc.background != background) {
                        xc.c = b;
                        xc.color = foreground;
                        xc.background = background;
                        xc.style = charStyle;
                        repaint = true;
                    }
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
