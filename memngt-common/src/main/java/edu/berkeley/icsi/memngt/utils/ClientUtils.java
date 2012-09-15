package edu.berkeley.icsi.memngt.utils;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.management.ManagementFactory;

import com.esotericsoftware.minlog.Log;

public final class ClientUtils {

	private static final int PAGE_SIZE = 4;

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

	public static int getPhysicalMemorySize(final int pid) {

		final String filename = "/proc/" + pid + "/statm";

		BufferedReader br = null;

		try {
			br = new BufferedReader(new FileReader(filename));
			final String line = br.readLine();
			if (line == null) {
				Log.error("Could not read " + filename);
				return -1;
			}

			final String[] fields = line.split(" ");
			if (fields.length < 2) {
				Log.error("Output of " + filename + " has unexpected format");
				return -1;
			}

			return Integer.parseInt(fields[1]) * PAGE_SIZE;

		} catch (FileNotFoundException fnfe) {
			return -1;
		} catch (IOException ioe) {
			Log.error("Error while reading " + filename + ": ", ioe);
			return -1;
		} catch (NumberFormatException nfe) {
			Log.error("Unable to parse output of " + filename + ": ", nfe);
			return -1;
		} finally {

			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
				}
			}
		}
	}
}
