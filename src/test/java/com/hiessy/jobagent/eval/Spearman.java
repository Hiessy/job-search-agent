package com.hiessy.jobagent.eval;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.IntStream;

/** Spearman rank correlation: Pearson correlation of the ranks, with tied values sharing their average rank. */
final class Spearman {

    private Spearman() {
    }

    static double rho(double[] a, double[] b) {
        if (a.length != b.length || a.length < 2) {
            throw new IllegalArgumentException("Need two series of the same length, at least 2");
        }
        return pearson(ranks(a), ranks(b));
    }

    static double[] ranks(double[] v) {
        Integer[] idx = IntStream.range(0, v.length).boxed().toArray(Integer[]::new);
        Arrays.sort(idx, Comparator.comparingDouble(i -> v[i]));
        double[] r = new double[v.length];
        int i = 0;
        while (i < idx.length) {
            int j = i;
            while (j + 1 < idx.length && v[idx[j + 1]] == v[idx[i]]) {
                j++;
            }
            double avg = (i + j) / 2.0 + 1;
            for (int k = i; k <= j; k++) {
                r[idx[k]] = avg;
            }
            i = j + 1;
        }
        return r;
    }

    private static double pearson(double[] x, double[] y) {
        double mx = Arrays.stream(x).average().orElse(0);
        double my = Arrays.stream(y).average().orElse(0);
        double num = 0;
        double dx = 0;
        double dy = 0;
        for (int i = 0; i < x.length; i++) {
            num += (x[i] - mx) * (y[i] - my);
            dx += (x[i] - mx) * (x[i] - mx);
            dy += (y[i] - my) * (y[i] - my);
        }
        return dx == 0 || dy == 0 ? 0 : num / Math.sqrt(dx * dy);
    }
}
