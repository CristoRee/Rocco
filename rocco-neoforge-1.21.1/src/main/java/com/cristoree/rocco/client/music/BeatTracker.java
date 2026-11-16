package com.cristoree.rocco.client.music;

// El ritmo de la cancion (pulsos por minuto), para que Rocco cante y baile a tiempo. Mira la energia
// del audio cada 10 ms, se queda con las subidas (los golpes) y busca cada cuanto se repiten
// (autocorrelacion). Tiene un resultado a los 12 segundos y lo afina hasta los 40.
final class BeatTracker
{
    private static final int SAMPLE_RATE = 48000;
    private static final int HOP = SAMPLE_RATE / 100;
    private static final int MAX_HOPS = 4000;
    private static final int FIRST_RESULT_HOPS = 1200;

    private final float[] onset = new float[MAX_HOPS];
    private int hops;
    private double hopEnergy;
    private int hopSamples;
    private double lastLogEnergy;
    private volatile double bpm;

    double bpm()
    {
        return this.bpm;
    }

    // Muestras mono de 16 bits a 48 kHz, en orden.
    void feed(short[] samples)
    {
        if (this.hops >= MAX_HOPS)
        {
            return;
        }
        for (short sample : samples)
        {
            double value = sample / 32768.0D;
            this.hopEnergy += value * value;
            if (++this.hopSamples == HOP)
            {
                double log = Math.log(this.hopEnergy / HOP + 1e-9D);
                this.onset[this.hops] = (float) Math.max(0.0D, log - this.lastLogEnergy);
                this.lastLogEnergy = log;
                this.hopEnergy = 0.0D;
                this.hopSamples = 0;
                this.hops++;
                if (this.hops >= FIRST_RESULT_HOPS && this.hops % 500 == 0 || this.hops == MAX_HOPS)
                {
                    estimate();
                }
                if (this.hops >= MAX_HOPS)
                {
                    return;
                }
            }
        }
    }

    private void estimate()
    {
        int n = this.hops;
        double mean = 0.0D;
        for (int i = 0; i < n; i++)
        {
            mean += this.onset[i];
        }
        mean /= n;

        // Entre 60 y 200 pulsos por minuto, con preferencia suave por los ritmos de siempre (~120).
        int minLag = 30;
        int maxLag = 100;
        double best = 0.0D;
        int bestLag = 0;
        double[] score = new double[maxLag + 2];
        for (int lag = minLag; lag <= maxLag + 1; lag++)
        {
            double sum = 0.0D;
            for (int i = lag; i < n; i++)
            {
                sum += (this.onset[i] - mean) * (this.onset[i - lag] - mean);
            }
            score[lag] = sum / (n - lag);
        }
        for (int lag = minLag; lag <= maxLag; lag++)
        {
            double tempo = 6000.0D / lag;
            double weight = Math.exp(-0.5D * Math.pow(Math.log(tempo / 120.0D) / Math.log(2.0D) / 0.9D, 2.0D));
            double value = score[lag] * weight;
            if (value > best)
            {
                best = value;
                bestLag = lag;
            }
        }
        if (bestLag == 0 || best <= 0.0D)
        {
            return;
        }
        // Afina entre los vecinos (parabola) para no quedar atado a los 10 ms.
        double left = score[bestLag - 1];
        double right = score[bestLag + 1];
        double middle = score[bestLag];
        double denominator = left - 2.0D * middle + right;
        double shift = denominator == 0.0D ? 0.0D : 0.5D * (left - right) / denominator;
        double tempo = 6000.0D / (bestLag + Math.max(-0.5D, Math.min(0.5D, shift)));
        while (tempo < 75.0D)
        {
            tempo *= 2.0D;
        }
        while (tempo > 165.0D)
        {
            tempo /= 2.0D;
        }
        this.bpm = tempo;
    }
}
