/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gui;

import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 *
 * @author jaraics
 */
class FadingPanel extends JPanel
        implements ActionListener {

    private volatile float opacity = 0f;
    private volatile boolean hidden = false;
    private Timer fadeTimer;
    private BufferedImage componentImage = null;
    private final float dOpacityShow = 0.03f;
    private final float dOpacityHide = 0.03f;
    private float deltaOpacity = dOpacityShow;
    private int rule;

    public void myShow(boolean withAnimation) {
        hidden = false;
        if (withAnimation) {
            deltaOpacity = dOpacityShow;
            opacity = 0f;
            beginFade();
        } else {
            opacity = 1f;
            repaint();
        }
    }

    public void hideNow() {
        hidden = true;
        opacity = 0f;
        repaint();
    }

    public void myHide(boolean withAnimation) {
        if (withAnimation) {
            deltaOpacity = -dOpacityHide;
            // do NOT reset opacity — fade from whatever it currently is
            beginFade();
        } else {
            opacity = 0f;
            repaint();
        }
    }

    private void beginFade() {
        if (fadeTimer != null) {
            fadeTimer.stop();
            fadeTimer = null;
        }
        componentImage = null;
        fadeTimer = new javax.swing.Timer(75, this);
        fadeTimer.setInitialDelay(0);
        fadeTimer.start();
    }

    public void actionPerformed(ActionEvent e) {
        if (hidden) {
            fadeEnd();
            return;
        }
        opacity += deltaOpacity;
        if (opacity > 1) {
            opacity = 1;
            fadeEnd();
        } else if (opacity < 0) {
            opacity = 0;
            fadeEnd();
        }
        repaint();
    }

    private void fadeEnd() {
        if(fadeTimer != null) {
            fadeTimer.stop();
            fadeTimer = null;

        }
        componentImage = null;
    }

    public void paint(Graphics g) {
        // Always erase first so old pixels don't bleed through when opacity < 1
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());

        if (opacity <= 0f) return;

        // Create an image for the button graphics if necessary
        if (componentImage == null || componentImage.getWidth() != getWidth() ||
                componentImage.getHeight() != getHeight()) {
            componentImage = getGraphicsConfiguration().
                    createCompatibleImage(getWidth(), getHeight());
        }
        Graphics gButton = componentImage.getGraphics();
        gButton.setClip(g.getClip());

        //  Have the superclass render the button for us
        super.paint(gButton);

        // Copy the button's image to the destination graphics, translucently
        Graphics2D g2d = (Graphics2D) g;
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
        g2d.drawImage(componentImage, 0, 0, null);
    }

}
