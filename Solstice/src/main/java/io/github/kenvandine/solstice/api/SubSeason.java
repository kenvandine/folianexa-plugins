package io.github.kenvandine.solstice.api;

/**
 * The five blend phases within a season's progress, per PLAN.md §3.2. Colors never snap between
 * seasons; SUB_1/SUB_2 blend from the previous season in, SUB_3/SUB_4 blend into the next season.
 */
public enum SubSeason {
    SUB_1(0.00, 0.09, 0.45),
    SUB_2(0.09, 0.18, 0.25),
    FULL(0.18, 0.84, 0.0),
    SUB_3(0.84, 0.92, 0.25),
    SUB_4(0.92, 1.00, 0.45);

    /** Inclusive lower bound of season progress [0,1) at which this phase begins. */
    public final double from;
    /** Exclusive upper bound of season progress [0,1) at which this phase ends. */
    public final double to;
    /**
     * Weight of the neighboring season (previous for SUB_1/SUB_2, next for SUB_3/SUB_4).
     * Zero for FULL, where the current season is 100%.
     */
    public final double neighborWeight;

    SubSeason(double from, double to, double neighborWeight) {
        this.from = from;
        this.to = to;
        this.neighborWeight = neighborWeight;
    }

    public static SubSeason forProgress(double progress) {
        for (SubSeason phase : values()) {
            if (progress >= phase.from && progress < phase.to) {
                return phase;
            }
        }
        return progress < 0 ? SUB_1 : SUB_4;
    }

    public boolean blendsPrevious() {
        return this == SUB_1 || this == SUB_2;
    }

    public boolean blendsNext() {
        return this == SUB_3 || this == SUB_4;
    }
}
