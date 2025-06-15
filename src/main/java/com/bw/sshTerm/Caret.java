package com.bw.sshTerm;

import javax.swing.*;
import java.awt.*;

/**
 * The caret, happily blinking if gained focus,
 */
public class Caret {

    Timer blinkTimer;

    private int blinkRate = 500;
    private boolean blinkVisible = true;
    private TerminalPane pane;

    private boolean visible = true;
    private int caretX = 0;
    private int caretY = 0;

    private int lastDrawnCursorX = -1;
    private int lastDrawnCursorY = -1;
    private boolean lastCursorDrawn = false;


    public Caret() {

    }

    public void setCaret(int x, int y) {
        caretX = x;
        caretY = y;
    }

    public int getCaretX() {
        return caretX;
    }

    public void setCaretX(int x) {
        caretX = x;
    }

    public int getCaretY() {
        return caretY;
    }

    public void setCaretY(int y) {
        caretY = y;
    }

    public void install(TerminalPane pane) {

        this.pane = pane;

        blinkTimer = new Timer(blinkRate, e -> {
            blinkVisible = !blinkVisible;
            drawCursor((Graphics2D) this.pane.getGraphics());
        });
        blinkTimer.setDelay(blinkRate);

    }

    /**
     * Draws the cursor.
     * If cursor position has changed, the old position is repainted together with the new cursor (via a pane-repaint).
     * To reduce redundant re-paints, the pane has to call {@link #caretIsCleared()} if the area is already repainted.
     */
    public void drawCursor(Graphics2D g2) {

        int newCursorX = pane.getLeftPageMargin() + caretX * pane.charWidth;
        int newCursorY = caretY * pane.charHeight;
        boolean needsUpdateOldPosition = (lastDrawnCursorX >= 0) && (newCursorX != lastDrawnCursorX || newCursorY != lastDrawnCursorY);

        if (needsUpdateOldPosition) {
            blinkVisible = true;
            blinkTimer.restart();
        }

        if (needsUpdateOldPosition || (blinkVisible && visible) != lastCursorDrawn) {

            Rectangle r = new Rectangle(newCursorX, newCursorY, pane.charWidth, pane.charHeight);

            if (needsUpdateOldPosition || !(blinkVisible && visible)) {
                // Erase (old) cursor
                r.width++;
                r.height++;
                if (needsUpdateOldPosition) {
                    Rectangle ur = new Rectangle(lastDrawnCursorX, lastDrawnCursorY, pane.charWidth + 1, pane.charHeight + 1);
                    r.add(ur);
                }
                lastDrawnCursorX = -1;
                lastCursorDrawn = false;
                pane.triggerRepaint(r);
            } else {
                g2.setPaint(Color.RED);
                g2.draw(r);
                lastDrawnCursorX = newCursorX;
                lastDrawnCursorY = newCursorY;
                lastCursorDrawn = true;
            }
        }
    }

    /**
     * Sets the caret visible or not.
     */
    public void setVisible(boolean visible) {
        if (this.visible != visible) {
            System.out.println("switched visible " + visible);
            this.visible = visible;
            pane.triggerRepaintCursor();
        }
        if (visible)
            blinkTimer.restart();
        else
            blinkTimer.stop();
    }

    /**
     * Resets blinking. Restarts blink timer and forces caret blink state to visible.
     */
    public void resetBlinking() {
        if (blinkTimer.isRunning())
            blinkTimer.restart();
        if (visible && !blinkVisible) {
            blinkVisible = true;
            pane.triggerRepaintCursor();
        }
    }

    /**
     * Resets the remembered old position.
     * The caret will not try to repaint the old caret position if the caret is moved.
     */
    public void caretIsCleared() {
        lastDrawnCursorX = -1;
        lastCursorDrawn = false;
    }
}
