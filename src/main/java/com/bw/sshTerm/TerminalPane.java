package com.bw.sshTerm;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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

    public List<char[]> topScrollBuffer = new ArrayList<>(100);
    public List<char[]> bottomScrollBuffer = new ArrayList<>(100);

    public List<char[]> term = new ArrayList<>(100);

    public boolean showCursor = true;
    protected int charWidth;
    protected int charHeight;

    protected int termWidth;
    protected int termHeight;

    /**
     * Zero based upper margin in range [0, termHeight-1[
     */
    protected int marginTop = 0;

    /**
     * Zero based lower margin in range [1, termHeight[
     */
    protected int marginBottom = 0;

    protected int ascent;
    int caretY = 0;
    int caretX = 0;
    char[][] lines = new char[0][0];
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

    @Override
    public Dimension getPreferredSize() {
        updateTerminalSpecs();
        return new Dimension(charWidth * termWidth, charHeight * Math.max(term.size(), termHeight));
    }

    public void moveCaret(int xd, int yd) {
        caretX += xd;
        caretY += yd;
        while (caretY > marginBottom) {
            scrollDown();
            caretY--;
        }
        while (caretY < marginTop) {
            scrollUp();
            caretY++;
        }
        repaint();
    }

    public void setCaretAbsolute(int x, int y) {
        if (marginBottom == 0) marginBottom = termHeight - 1;
        System.out.println("setCarAbs " + x + "," + y + " [" + marginTop + "," + marginBottom + "]");
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        caretX = x;
        caretY = y;
        while (caretY > marginBottom) {
            caretY--;
            scrollDown();
        }
        while (caretY < marginTop) {
            caretY++;
            scrollUp();
        }
        repaint();
    }

    public int getCaretX() {
        return caretX;
    }

    public int getCaretY() {
        return caretY;
    }

    protected void ensureSpace() {

        while (term.size() > termHeight)
            term.remove(term.size() - 1);
        while (term.size() < termHeight) {
            term.add(new char[termWidth]);
        }
    }

    protected void scrollDown() {
        topScrollBuffer.add(term.remove(marginTop));
        if (bottomScrollBuffer.isEmpty())
            term.add(marginBottom, new char[termWidth]);
        else
            term.add(marginBottom, bottomScrollBuffer.remove(bottomScrollBuffer.size() - 1));
        System.out.println("After ScrollDown: [" + marginTop + "," + marginBottom + "] term:" + term.size());
        repaint();
    }

    protected void scrollUp() {
        bottomScrollBuffer.add(term.remove(marginBottom));
        term.add(marginTop, topScrollBuffer.remove(topScrollBuffer.size() - 1));
        System.out.println("After ScrollUp: [" + marginTop + "," + marginBottom + "] term:" + term.size());
        repaint();
    }


    /**
     * Set char at the terminal
     *
     * @param x The column in terminal. [0 - termWidth[
     * @param y The row in terminal. [0 - termHeight[
     * @param b The character to set
     */
    public void setCharAt(int x, int y, char b) {
        try {
            System.out.println("char (" + x + "," + y + ")=" + (b < ' ' ? "Ox" + Integer.toHexString(b) : "" + b));
            if (y >= 0) {
                while (y > marginBottom) {
                    scrollDown();
                    --y;
                }
                char[] l = term.get(y);
                if (l.length <= x) {
                    l = Arrays.copyOf(l, x + 10);
                    term.set(y, l);
                }
                l[x--] = b;
                while (x >= 0 && l[x] == 0) {
                    l[x--] = ' ';
                }
                // TODO
                repaint();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setChar(char b) {
        setCharAt(caretX, caretY, b);
    }

    @Override
    public void paintComponent(Graphics g) {
        int x = charWidth * 3;
        int y = ascent;
        Graphics2D g2 = (Graphics2D) g.create();
        int i = 1;
        try {
            synchronized (term) {
                lines = term.toArray(lines);
            }
            for (char[] line : lines) {
                if (line == null) {
                    break;
                }
                g2.setPaint(Color.RED);
                g2.drawString(String.format("%03d", i++), 0, y);
                g2.setPaint(getForeground());
                g2.drawChars(line, 0, Math.min(termWidth, line.length), x, y);
                y += charHeight;
            }
            if (showCursor)
                g2.drawRect(x + caretX * charWidth, caretY * charHeight, charWidth, charHeight);
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
        // TODO: fix this for now.
        d.width = newCharWidth * 80;
        d.height = newCharHeight * 40;

        int newTermWitdh = d.width / newCharWidth;
        int newTermHeight = d.height / newCharHeight;

        if (newTermHeight > 0 && newTermWitdh > 0 &&
                (charHeight != newCharHeight ||
                        charWidth != newCharWidth ||
                        termWidth != newTermWitdh ||
                        termHeight != newTermHeight)) {

            charHeight = newCharHeight;
            charWidth = newCharWidth;
            ascent = metrics.getAscent();
            termWidth = newTermWitdh;

            if (marginBottom == (termHeight - 1))
                marginBottom = newTermHeight - 1;
            termHeight = newTermHeight;

            ensureSpace();

            System.out.printf("\nNew Terminal Dimension: %d x %d. char %d x %d\n", termWidth, termHeight, charWidth, charHeight);

            int[] newTerminalSpec = new int[]{termWidth, termHeight, charWidth, charHeight};
            firePropertyChange(PROPERTY_TERM_SIZE, terminalSpec, newTerminalSpec);
        }
    }

    public void clear() {
        marginTop = 0;
        marginBottom = termHeight - 1;
        caretX = 0;
        caretY = 0;
        topScrollBuffer.clear();
        bottomScrollBuffer.clear();
        term.clear();
        ensureSpace();


        repaint();
    }

    public void deleteChar() {
        char[] line = term.get(caretY);
        for (int x = caretX; x < (line.length - 1); ++x) {
            line[x] = line[x + 1];
        }
        line[line.length - 1] = 0;
        repaint();
    }

    public void insert(char c) {
        char[] line = term.get(caretY);
        if (line.length <= caretX) {
            line = Arrays.copyOf(line, caretX + 10);
            term.set(caretY, line);
        }
        for (int x = (line.length - 1); x > caretX; --x) {
            line[x] = line[x - 1];
        }
        line[caretX] = c;
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
        }
        repaint();
    }
}
