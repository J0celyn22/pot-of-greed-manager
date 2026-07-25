package Utils;

import java.util.Locale;

/**
 * Centralizes price/percentage parsing and formatting so both sides agree on
 * a decimal separator, regardless of the running JVM's default locale.
 * <p>
 * Previously, formatting went through {@code String.format("%.2f", ...)}
 * with no {@link Locale} pinned, so it silently followed the OS locale (a
 * period on a US-locale machine, a comma on a French-locale one), while
 * parsing went through {@code Float.parseFloat}, which only ever accepts a
 * period. On any comma-locale machine, a price the app had just formatted
 * itself could no longer be read back -- logged as
 * "Could not parse ... value: 0,00". This class fixes that by pinning
 * output to a period unconditionally ({@link Locale#US}), matching what
 * {@code Float.parseFloat} always expects -- the simpler of the two ways to
 * make both sides agree, since it needs no special-casing on the parse side.
 * <p>
 * {@link #parse(String)} still tolerates a comma too, as a one-line safety
 * net for any value that may already be stored with one (e.g. written by
 * this app before this fix, on a comma-locale machine) -- not because
 * output will ever produce one anymore.
 */
public final class PriceFormat {

    private PriceFormat() {
    }

    /**
     * Parses a price/percentage string. Tolerates a comma in place of the
     * decimal point, so any pre-existing comma-formatted data (e.g. written
     * before this fix) keeps working, even though output is always
     * period-based going forward.
     *
     * @throws NumberFormatException if the value still isn't a valid number
     *                               after normalizing the separator
     * @throws NullPointerException  if {@code value} is null
     */
    public static float parse(String value) {
        return Float.parseFloat(value.replace(",", "."));
    }

    /**
     * Formats a value to two decimal places using a period as the decimal
     * separator, regardless of the running JVM's default locale.
     */
    public static String format2(float value) {
        return String.format(Locale.US, "%.2f", value);
    }

    /**
     * Formats a value to one decimal place using a period as the decimal
     * separator, regardless of the running JVM's default locale.
     */
    public static String format1(float value) {
        return String.format(Locale.US, "%.1f", value);
    }
}