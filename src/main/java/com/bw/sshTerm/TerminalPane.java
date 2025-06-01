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
    public List<char[]> term = new ArrayList<>(100);
    public boolean showCursor = true;
    protected int charWidth;
    protected int charHeight;
    protected int termWidth;
    protected int termHeight;
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
        repaint();
    }

    public void setCaretAbsolute(int x, int y) {
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        caretX = x;
        caretY = y;
        repaint();
    }

    public int getCaretX() {
        return caretX;
    }

    public int getCaretY() {
        return caretY;
    }

    protected void ensureSpace(int yOffset) {
        while (term.size() <= yOffset) {
            term.add(new char[termWidth]);
        }
    }

    public void setCharAt(int x, int y, char b) {
        try {
            ensureSpace(y);
            char[] l = term.get(y);
            if (l.length <= x) {
                l = Arrays.copyOf(l, x + 10);
                term.set(y, l);
            }
            System.out.println("char (" + x + "," + y + ")=" + b);
            l[x--] = b;
            while (x >= 0 && l[x] == 0) {
                l[x--] = ' ';
            }
            // TODO
            repaint();
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
        if (d.width < 100)
            d.width = newCharWidth * 100;
        if (d.height < 100)
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
            termHeight = newTermHeight;
            System.out.printf("\nNew Terminal Dimension: %d x %d. char %d x %d\n", termWidth, termHeight, charWidth, charHeight);

            int[] newTerminalSpec = new int[]{termWidth, termHeight, charWidth, charHeight};
            firePropertyChange(PROPERTY_TERM_SIZE, terminalSpec, newTerminalSpec);
        }
    }

    public void clear() {
        caretX = 0;
        caretY = 0;
        term.clear();
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
}
