package org.example.wavelio.ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.example.wavelio.model.SpectrogramResult;

import java.util.Arrays;

public class SpectrogramCanvas extends Canvas {

    private static final Band[] BANDS = new Band[] {
        new Band("Bass", 20.0, 120.0, Color.web("#4FC3F7", 0.18)),
        new Band("Low-mid", 120.0, 500.0, Color.web("#81C784", 0.16)),
        new Band("Mid", 500.0, 2000.0, Color.web("#FFD54F", 0.14)),
        new Band("High", 2000.0, 10000.0, Color.web("#FF8A65", 0.12))
    };

    private static final Color[] PALETTE = new Color[] {
        Color.web("#000004"),
        Color.web("#1B0C41"),
        Color.web("#4A0C6B"),
        Color.web("#781C6D"),
        Color.web("#A52C60"),
        Color.web("#CF4446"),
        Color.web("#ED6925"),
        Color.web("#FB9A06"),
        Color.web("#F6C63A"),
        Color.web("#F7E56A")
    };
    private static final double LEFT_AXIS_WIDTH = 64.0;
    private static final double AXIS_TICK_LENGTH = 6.0;
    private static final double[] AXIS_TICKS_HZ = new double[] {100, 200, 500, 1000, 2000, 5000, 10000};

    private SpectrogramResult currentResult;
    private boolean showBands = true;

    public SpectrogramCanvas() {
        widthProperty().addListener((obs, oldV, newV) -> draw(currentResult));
        heightProperty().addListener((obs, oldV, newV) -> draw(currentResult));
    }

    public void setSpectrogram(SpectrogramResult result) {
        currentResult = result;
        draw(result);
    }

    public void setShowBands(boolean showBands) {
        this.showBands = showBands;
        draw(currentResult);
    }

    private void draw(SpectrogramResult result) {
        GraphicsContext gc = getGraphicsContext2D();
        double width = getWidth();
        double height = getHeight();
        gc.clearRect(0, 0, width, height);
        gc.setFill(Color.web("#121212"));
        gc.fillRect(0, 0, width, height);

        if (result == null || result.magnitudesDb() == null || result.magnitudesDb().length == 0) {
            return;
        }
        if (width <= LEFT_AXIS_WIDTH + 1 || height <= 1) {
            return;
        }

        double[][] db = result.magnitudesDb();
        int timeFrames = db.length;
        int bins = db[0].length;
        if (bins == 0) {
            return;
        }
        double plotX = LEFT_AXIS_WIDTH;
        double plotWidth = width - plotX;
        double plotHeight = height;

        double[] contrast = computeContrastBounds(db);
        double floorDb = contrast[0];
        double ceilDb = contrast[1];

        for (int x = 0; x < (int) plotWidth; x++) {
            int frame = Math.min(timeFrames - 1, (int) Math.floor((x / plotWidth) * timeFrames));
            for (int y = 0; y < (int) plotHeight; y++) {
                int binFromBottom = Math.min(bins - 1, (int) Math.floor((y / plotHeight) * bins));
                int bin = (bins - 1) - binFromBottom;
                double v = db[frame][bin];
                double normalized = normalizeDb(v, floorDb, ceilDb);
                gc.getPixelWriter().setColor((int) (plotX + x), y, heatColor(normalized));
            }
        }

        double maxHz = result.frequencyAxisHz().length == 0
            ? (result.sampleRate() / 2.0)
            : result.frequencyAxisHz()[result.frequencyAxisHz().length - 1];
        if (showBands) {
            drawBandGuides(gc, plotX, plotWidth, plotHeight, maxHz);
        }
        drawFrequencyAxis(gc, plotX, plotHeight, maxHz);

        gc.setStroke(Color.web("#2f2f2f"));
        gc.strokeRect(plotX + 0.5, 0.5, plotWidth - 1.0, plotHeight - 1.0);
    }

    private static double normalizeDb(double db, double floor, double ceil) {
        if (ceil <= floor) {
            return 0.0;
        }
        double clamped = Math.max(floor, Math.min(ceil, db));
        return (clamped - floor) / (ceil - floor);
    }

    private static Color heatColor(double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        double scaled = clamped * (PALETTE.length - 1);
        int idx = (int) Math.floor(scaled);
        int next = Math.min(PALETTE.length - 1, idx + 1);
        double localT = scaled - idx;
        return PALETTE[idx].interpolate(PALETTE[next], localT);
    }

    private static void drawBandGuides(GraphicsContext gc, double plotX, double plotWidth, double plotHeight, double maxHz) {
        if (maxHz <= 0.0) {
            return;
        }
        gc.setLineWidth(1.0);
        for (Band band : BANDS) {
            double low = Math.max(0.0, Math.min(maxHz, band.lowHz()));
            double high = Math.max(0.0, Math.min(maxHz, band.highHz()));
            if (high <= low) {
                continue;
            }
            double yTop = yForHz(high, maxHz, plotHeight);
            double yBottom = yForHz(low, maxHz, plotHeight);
            double bandHeight = Math.max(1.0, yBottom - yTop);

            gc.setFill(band.fillColor());
            gc.fillRect(plotX, yTop, plotWidth, bandHeight);

            gc.setStroke(Color.web("#F0F0F0", 0.28));
            gc.strokeLine(plotX, yTop, plotX + plotWidth, yTop);
            gc.strokeLine(plotX, yBottom, plotX + plotWidth, yBottom);
        }
    }

    private static void drawFrequencyAxis(GraphicsContext gc, double plotX, double plotHeight, double maxHz) {
        gc.setStroke(Color.web("#B8B8B8", 0.75));
        gc.setFill(Color.web("#D8D8D8", 0.92));
        gc.setLineWidth(1.0);

        gc.strokeLine(plotX, 0.0, plotX, plotHeight);
        for (double hz : AXIS_TICKS_HZ) {
            if (hz <= 0.0 || hz > maxHz) {
                continue;
            }
            double y = yForHz(hz, maxHz, plotHeight);
            gc.strokeLine(plotX - AXIS_TICK_LENGTH, y, plotX, y);
            gc.fillText(formatHz(hz), 6.0, y + 4.0);
        }
    }

    private static String formatHz(double hz) {
        if (hz >= 1000.0) {
            return (hz % 1000 == 0) ? ((int) (hz / 1000)) + "k" : String.format("%.1fk", hz / 1000.0);
        }
        return Integer.toString((int) hz);
    }

    private static double[] computeContrastBounds(double[][] db) {
        int total = db.length * db[0].length;
        if (total <= 0) {
            return new double[] {-120.0, 0.0};
        }
        double[] flat = new double[total];
        int idx = 0;
        for (double[] row : db) {
            for (double v : row) {
                flat[idx++] = v;
            }
        }
        Arrays.sort(flat);
        int lowIdx = (int) Math.floor((flat.length - 1) * 0.05);
        int highIdx = (int) Math.floor((flat.length - 1) * 0.98);
        double low = flat[Math.max(0, Math.min(flat.length - 1, lowIdx))];
        double high = flat[Math.max(0, Math.min(flat.length - 1, highIdx))];
        if (high - low < 1.0) {
            high = low + 1.0;
        }
        return new double[] {low, high};
    }

    private static double yForHz(double hz, double maxHz, double height) {
        return (1.0 - (hz / maxHz)) * height;
    }

    private record Band(String name, double lowHz, double highHz, Color fillColor) {
    }
}

