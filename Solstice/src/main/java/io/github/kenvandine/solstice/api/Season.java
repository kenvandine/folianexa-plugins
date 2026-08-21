package io.github.kenvandine.solstice.api;

/** The four seasons. Order matters — {@link #next()} relies on ordinal wraparound. */
public enum Season {
    SPRING,
    SUMMER,
    AUTUMN,
    WINTER;

    public Season next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public Season previous() {
        return values()[(ordinal() + values().length - 1) % values().length];
    }
}
