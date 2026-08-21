package io.github.kenvandine.solstice.storage;

/** Immutable per-player toggle preferences (PLAN.md §3.7). Replaced wholesale on each toggle. */
public record PlayerPrefs(boolean temperatureDisplay, boolean seasonColors, boolean seasonParticles, boolean fahrenheit) {

    public static PlayerPrefs defaults() {
        return new PlayerPrefs(true, true, true, false);
    }

    public PlayerPrefs withTemperatureDisplay(boolean v) {
        return new PlayerPrefs(v, seasonColors, seasonParticles, fahrenheit);
    }

    public PlayerPrefs withSeasonColors(boolean v) {
        return new PlayerPrefs(temperatureDisplay, v, seasonParticles, fahrenheit);
    }

    public PlayerPrefs withSeasonParticles(boolean v) {
        return new PlayerPrefs(temperatureDisplay, seasonColors, v, fahrenheit);
    }

    public PlayerPrefs withFahrenheit(boolean v) {
        return new PlayerPrefs(temperatureDisplay, seasonColors, seasonParticles, v);
    }
}
