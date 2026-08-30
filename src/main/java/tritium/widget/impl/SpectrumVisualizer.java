package tritium.widget.impl;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class SpectrumVisualizer {

    @Getter
    private final int sampleRate;
    @Getter
    private final int fftSize;
    @Getter
    private final int numBands;
    private final float minDB = -90f;
    private final float maxDB = 0f;

    @Getter
    private final List<FrequencyBand> bands = new ArrayList<>();
    private final float[] bandMagnitudes;
    private final int maxBin;

    private final float lowFreqCutoff = 500.0f;

    public SpectrumVisualizer(int sampleRate, int fftSize, int numBands) {
        this.sampleRate = sampleRate;
        this.fftSize = fftSize;
        this.numBands = numBands;
        this.maxBin = fftSize / 2 - 1;

        generateFrequencyBands();
        this.bandMagnitudes = new float[bands.size()];
    }

    private void generateFrequencyBands() {
        float maxFreq = 14000;
        float minFreq = 20.0f;

        generateEnhancedBarkBands(minFreq, maxFreq);
    }

    private void generateEnhancedBarkBands(float minFreq, float maxFreq) {
        float lowFreqFactor = .3f;
        int lowFreqBands = (int) (numBands * lowFreqFactor);
        int highFreqBands = numBands - lowFreqBands;

        float lowFreqMax = Math.min(lowFreqCutoff, maxFreq);
        generateDenseBarkBands(minFreq, lowFreqMax, lowFreqBands);

        if (lowFreqMax < maxFreq) {
            float barkMin = freqToBark(lowFreqMax * .21f);
            float barkMax = freqToBark(maxFreq);
            float barkStep = (barkMax - barkMin) / highFreqBands;

            for (int i = 0; i < highFreqBands; i++) {
                float lowFreq = barkToFreq(barkMin + i * barkStep);
                float highFreq = barkToFreq(barkMin + (i + 1) * barkStep);
                float centerFreq = (lowFreq + highFreq) / 2;
                int lowBin = freqToBin(lowFreq);
                int highBin = freqToBin(highFreq);
                lowBin = Math.max(0, Math.min(maxBin, lowBin));
                highBin = Math.max(lowBin, Math.min(maxBin, highBin));
                bands.add(new FrequencyBand(centerFreq, lowFreq, highFreq, lowBin, highBin));
            }
        }
    }
    private void generateDenseBarkBands(float minFreq, float maxFreq, int bandCount) {

        int linearBands = bandCount / 5;
        int barkBands = bandCount - linearBands;

        float midFreq = minFreq + (maxFreq - minFreq) * .3f;

        float linearStep = (midFreq - minFreq) / linearBands;
        for (int i = 0; i < linearBands; i++) {
            float lowFreq = minFreq + i * linearStep;
            float highFreq = minFreq + (i + 1) * linearStep;
            float centerFreq = (lowFreq + highFreq) / 2;

            int lowBin = freqToBin(lowFreq);
            int highBin = freqToBin(highFreq);
            lowBin = Math.max(0, Math.min(maxBin, lowBin));
            highBin = Math.max(lowBin, Math.min(maxBin, highBin));

            bands.add(new FrequencyBand(centerFreq, lowFreq, highFreq, lowBin, highBin));
        }

    }

    private float freqToBark(float freq) {
        return 13.0f * (float) Math.atan(0.00076f * freq) + 3.5f * (float) Math.atan((freq / 7500.0f) * (freq / 7500.0f));
    }

    private float barkToFreq(float bark) {
        return 600.0f * (float) Math.sinh(bark / 4.0f);
    }

    private int freqToBin(float freq) {
        return Math.round(freq * fftSize / sampleRate);
    }

    public float[] processFFT(float[] magnitudes) {
        if (magnitudes.length < fftSize / 2) {
            return bandMagnitudes;
        }

        for (int i = 0; i < bands.size(); i++) {
            FrequencyBand band = bands.get(i);
            float energy = 0;
            int binCount = 0;

            for (int bin = band.lowBin; bin <= band.highBin && bin < magnitudes.length; bin++) {
                float magnitude = magnitudes[bin] * magnitudes[bin];

                magnitude *= 0.25f * 0.25f;

                energy += magnitude;
                binCount++;
            }

            if (binCount > 0) {
                energy = (float) Math.sqrt(energy / binCount);
            }

            float db = energy < 1e-10f ? minDB : 20 * (float) Math.log10(energy);

            if (band.centerFreq < lowFreqCutoff) {
                db -= (lowFreqCutoff - band.centerFreq) / lowFreqCutoff * 18f;
            }

            double max = dBToLinear(-30);
            double min = dBToLinear(-100);
            double value = dBToLinear(db) * 2;

            float sensitivity = 1.0f;

            float normalized = (float) Math.pow((value - min) / (max - min), 1 / (1.8 * sensitivity));
            bandMagnitudes[i] = normalized;
        }

        for (int i = 0; i < bandMagnitudes.length; i++) {
            if (!Float.isFinite(bandMagnitudes[i])) {
                bandMagnitudes[i] = 0;
            }
        }

        return bandMagnitudes;
    }

    private float dBToLinear(float input) {
        return (float) Math.pow(10, input / 20);
    }

    public static class FrequencyBand {
        final float centerFreq;
        final float lowFreq;
        final float highFreq;
        final int lowBin;
        final int highBin;

        public FrequencyBand(float center, float low, float high, int lowBin, int highBin) {
            this.centerFreq = center;
            this.lowFreq = low;
            this.highFreq = high;
            this.lowBin = lowBin;
            this.highBin = highBin;
        }
    }
}
