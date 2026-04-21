package org.example.wavelio.ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;

public class WaveformCanvas extends Canvas {

    private static final Color BG = Color.web("#202020");
    private static final Color CENTER = Color.web("#444444");
    private static final Color CH_L = Color.web("#4fc3f7");
    private static final Color CH_R = Color.web("#ff8a65");
    private static final Color SELECTION_FILL = Color.web("#90caf9", 0.22);
    private static final Color SELECTION_EDGE = Color.web("#90caf9", 0.85);

    private double[][] channels;
    private Double selectionStartX;
    private Double selectionEndX;
    private SelectionListener selectionListener;

    public WaveformCanvas() {
        widthProperty().addListener((obs, oldV, newV) -> draw());
        heightProperty().addListener((obs, oldV, newV) -> draw());

        setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            selectionStartX = clampX(e.getX());
            selectionEndX = selectionStartX;
            notifySelection();
            draw();
        });
        setOnMouseDragged(e -> {
            if (selectionStartX == null) return;
            selectionEndX = clampX(e.getX());
            notifySelection();
            draw();
        });
        setOnMouseReleased(e -> {
            if (selectionStartX == null) return;
            selectionEndX = clampX(e.getX());
            notifySelection();
            draw();
        });
    }

    public void setChannels(double[][] channels) {
        this.channels = channels;
        clearSelection();
        draw();
    }

    public void setSelectionListener(SelectionListener listener) {
        this.selectionListener = listener;
    }

    public void clearSelection() {
        selectionStartX = null;
        selectionEndX = null;
        notifySelection();
    }

    public boolean hasSelection() {
        return selectionStartX != null && selectionEndX != null && Math.abs(selectionEndX - selectionStartX) >= 1.0;
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

        drawSelectionOverlay(gc, w, h);
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

    private void drawSelectionOverlay(GraphicsContext gc, double width, double height) {
        if (selectionStartX == null || selectionEndX == null) return;
        double x0 = Math.min(selectionStartX, selectionEndX);
        double x1 = Math.max(selectionStartX, selectionEndX);
        if (x1 - x0 < 1.0) return;

        gc.setFill(SELECTION_FILL);
        gc.fillRect(x0, 0, x1 - x0, height);
        gc.setStroke(SELECTION_EDGE);
        gc.setLineWidth(1.0);
        gc.strokeLine(x0, 0, x0, height);
        gc.strokeLine(x1, 0, x1, height);
    }

    private void notifySelection() {
        if (selectionListener == null) return;
        double w = getWidth();
        if (w <= 0) return;
        if (selectionStartX == null || selectionEndX == null) {
            selectionListener.onSelection(OptionalSelection.empty());
            return;
        }
        double a = clamp01(selectionStartX / w);
        double b = clamp01(selectionEndX / w);
        selectionListener.onSelection(new OptionalSelection(true, Math.min(a, b), Math.max(a, b)));
    }

    private double clampX(double x) {
        return Math.max(0.0, Math.min(getWidth(), x));
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    public interface SelectionListener {
        void onSelection(OptionalSelection selection);
    }

    public record OptionalSelection(boolean present, double startRatio, double endRatio) {
        public static OptionalSelection empty() {
            return new OptionalSelection(false, 0.0, 0.0);
        }
    }
}

