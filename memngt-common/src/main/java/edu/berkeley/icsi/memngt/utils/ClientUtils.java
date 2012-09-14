package edu.berkeley.icsi.memngt.utils;

import java.lang.management.ManagementFactory;

public final class ClientUtils {

	private ClientUtils() {
	}

	/**
	 * Returns the process ID of the JVM executing this program.
	 * 
	 * @return the process ID of the JVM executing this program or <code>-1</code> if the ID could not be determined
	 */
	public static int getPID() {

		final String name = ManagementFactory.getRuntimeMXBean().getName();
		if (name == null) {
			return -1;
		}

		final String[] splits = name.split("@");
		if (splits == null) {
			return -1;
		}

		if (splits.length == 0) {
			return -1;
		}

		try {
			return Integer.parseInt(splits[0]);
		} catch (NumberFormatException e) {
			return -1;
		}
	}
}
