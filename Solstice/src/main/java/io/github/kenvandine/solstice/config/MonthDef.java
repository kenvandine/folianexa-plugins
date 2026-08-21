package io.github.kenvandine.solstice.config;

public record MonthDef(String name, int days, long dayTicks, long nightTicks) {
    public long fullLengthTicks() {
        return dayTicks + nightTicks;
    }
}
