package edu.berkeley.icsi.memngt.daemon;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.esotericsoftware.minlog.Log;

class Utils {

	private static final Pattern MEMINFO_PATTERN = Pattern.compile("^\\w+:\\s*(\\d+)\\skB$");

	private Utils() {
	}

	static boolean verifyPortBinding(final int pid, final int port) {

		// TODO: Implement me

		return true;
	}

	/**
	 * Returns the amount of free physical memory (including the memory that is currently used by the kernel for buffers
	 * and caching) in kilobytes.
	 * 
	 * @return the amount of free physical memory in kilobytes or <code>-1</code> if the amount could not be determined
	 */
	static int getFreePhysicalMemory() {

		int freeMemory = 0;
		int matches = 0;

		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader("/proc/meminfo"));
			String line = br.readLine();
			while (line != null) {

				if (line.startsWith("MemFree:")) {
					final int val = extractMeminfoValue(line);
					if (val == -1) {
						return -1;
					}
					freeMemory += val;
					++matches;
				} else if (line.startsWith("Cached:")) {
					final int val = extractMeminfoValue(line);
					if (val == -1) {
						return -1;
					}
					freeMemory += val;
					++matches;
				} else if (line.startsWith("Buffers:")) {
					final int val = extractMeminfoValue(line);
					if (val == -1) {
						return -1;
					}
					freeMemory += val;
					++matches;
				}

				// We got all the numbers we are interested in
				if (matches == 3) {
					break;
				}

				line = br.readLine();
			}

		} catch (IOException ioe) {
			Log.error("Error reading /proc/meminfo: ", ioe);
			return -1;
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
				}
			}
		}

		return freeMemory;
	}

	private static int extractMeminfoValue(final String line) {

		final Matcher matcher = MEMINFO_PATTERN.matcher(line);
		if (!matcher.matches()) {
			return -1;
		}

		try {
			return Integer.parseInt(matcher.group(1));
		} catch (NumberFormatException nfe) {
			Log.debug("Unable to parse " + matcher.group(1), nfe);
		}

		return -1;
	}
}
