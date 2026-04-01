package org.example.wavelio.ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class WaveformCanvas extends Canvas {

    private static final Color BG = Color.web("#202020");
    private static final Color CENTER = Color.web("#444444");
    private static final Color CH_L = Color.web("#4fc3f7");
    private static final Color CH_R = Color.web("#ff8a65");

    private double[][] channels;

    public WaveformCanvas() {
        widthProperty().addListener((obs, oldV, newV) -> draw());
        heightProperty().addListener((obs, oldV, newV) -> draw());
    }

    public void setChannels(double[][] channels) {
        this.channels = channels;
        draw();
    }

    private void draw() {
        GraphicsContext gc = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();
        gc.setFill(BG);
        gc.fillRect(0, 0, w, h);
        gc.setStroke(CENTER);
        gc.strokeLine(0, h / 2.0, w, h / 2.0);

        if (channels == null || channels.length == 0) {
            return;
        }
        drawChannelLine(gc, channels[0], w, h, CH_L);
        if (channels.length > 1) {
            drawChannelLine(gc, channels[1], w, h, CH_R);
        }
    }

    private void drawChannelLine(GraphicsContext gc, double[] values, double width, double height, Color color) {
        if (values == null || values.length < 2) {
            return;
        }
        gc.setStroke(color);
        gc.setLineWidth(1.0);
        double centerY = height / 2.0;
        double amplitude = height * 0.45;
        for (int i = 1; i < values.length; i++) {
            double x0 = (i - 1) * (width / (values.length - 1));
            double x1 = i * (width / (values.length - 1));
            double y0 = centerY - values[i - 1] * amplitude;
            double y1 = centerY - values[i] * amplitude;
            gc.strokeLine(x0, y0, x1, y1);
        }
    }
}

