/**
 *    DO WHAT THE FUCK YOU WANT TO PUBLIC LICENSE
 *                   Version 2, December 2004
 *
 *Copyright (C) 2004 Sam Hocevar <sam@hocevar.net>
 *
 * Everyone is permitted to copy and distribute verbatim or modified
 * copies of this license *ocument, and changing it is allowed as long
 * as the name is changed.*
 *
 *           DO WHAT THE FUCK YOU WANT TO PUBLIC LICENSE
 *  TERMS AND CONDITIONS FOR COPYING, DISTRIBUTION AND MODIFICATION
 *
 *  0. You just DO WHAT THE FUCK YOU WANT TO.
 * 
 */
package fr.mvanbesien.b3.cli.headless;

import java.util.Date;

/**
 * 
 * Implementation that displays readable time from two timestamps/dates
 * 
 * @author mvanbesien <mvaawl@gmail.com>
 *
 */
public final class TimeMagnifier {

	private static final String[] SIGNS = { "earlier than", "later than" };

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

}
