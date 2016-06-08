package fr.mvanbesien.b3.cli.headless;

import java.util.Date;

public final class TimeMagnifier {

	private static final String[] SIGNS = { "earlier", "later" };

	private static final long[] GAPS = { 1000, 60, 60, 24, 365 };

	private static final String[] UNITS = { "millisecond", "second", "minute", "hour", "day" };

	private TimeMagnifier() {
	}

	public static String magnifyTimeDifference(final Date begin, final Date end) {
		if (begin == null || end == null) {
			return "";
		}
		return magnifyTimeDifference(begin.getTime(), end.getTime());
	}

	public static String magnifyTimeDifference(final long begin, final long end) {
		if (begin == end) {
			return "at same time";
		}

		String sign = begin - end > 0 ? SIGNS[0] : SIGNS[1];
		long difference = Math.abs(begin - end);

		for (int i = 0; i < UNITS.length; i++) {
			if (difference < GAPS[i]) {
				return String.format("%d %s%s %s", difference, UNITS[i], difference > 1 ? "s" : "", sign);
			}
			difference = difference / GAPS[i];
		}

		return String.format("%d year%s %s", difference, difference > 1 ? "s" : "", sign);

	}

	public static void main(String[] args) {
		Date d = new Date();
		Date d2 = new Date(System.currentTimeMillis() - 86400000L * 2 * 365);
		System.out.println(magnifyTimeDifference(d, d2));
	}

}
