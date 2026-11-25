package com.hiessy.jobagent.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class SpearmanTest {

    @Test
    void perfectAndReversedOrder() {
        assertThat(Spearman.rho(new double[] {1, 2, 3, 4}, new double[] {10, 20, 30, 40})).isCloseTo(1.0, within(1e-9));
        assertThat(Spearman.rho(new double[] {1, 2, 3, 4}, new double[] {40, 30, 20, 10})).isCloseTo(-1.0, within(1e-9));
    }

    @Test
    void tiesShareTheirAverageRank() {
        assertThat(Spearman.ranks(new double[] {10, 20, 20, 5})).containsExactly(2, 3.5, 3.5, 1);
        // Pearson correlation of the average ranks [1, 2.5, 2.5, 4, 5] and [2, 1, 3.5, 3.5, 5], computed in Python
        assertThat(Spearman.rho(new double[] {1, 2, 2, 3, 5}, new double[] {2, 1, 4, 4, 5}))
                .isCloseTo(0.7631578947368421, within(1e-9));
    }
}
