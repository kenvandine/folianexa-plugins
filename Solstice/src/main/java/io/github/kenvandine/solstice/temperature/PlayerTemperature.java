package io.github.kenvandine.solstice.temperature;

/** Latest computed temperature snapshot for a player. Replaced wholesale every recalculation. */
public record PlayerTemperature(double airTemperatureC, double apparentTemperatureC, long computedAtMillis) {
}
