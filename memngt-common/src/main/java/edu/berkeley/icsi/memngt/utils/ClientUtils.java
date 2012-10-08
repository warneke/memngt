package edu.berkeley.icsi.memngt.utils;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.esotericsoftware.minlog.Log;

public final class ClientUtils {

	/**
	 * The assumed page size in kilobytes.
	 */
	private static final int PAGE_SIZE = 4; // TODO; Detect this automatically.

	/**
	 * The required Java virtual machine specification vendor.
	 */
	private static final String REQUIRED_SPEC_VENDOR = "Sun Microsystems Inc.";

	/**
	 * Regular expression to match the MinHeapFreeRatio parameter of the JVM.
	 */
	private static final Pattern JVM_MIN_HEAP_FREE_RATIO = Pattern.compile("-XX:MinHeapFreeRatio=(\\d+)");

	/**
	 * Regular expression to match the MaxHeapFreeRatio parameter of the JVM.
	 */
	private static final Pattern JVM_MAX_HEAP_FREE_RATIO = Pattern.compile("-XX:MaxHeapFreeRatio=(\\d+)");

	/**
	 * Private constructor to prevent instantiation.
	 */
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

	/**
	 * Returns the physical memory size for the process with the given ID as observed by the operating system.
	 * 
	 * @param pid
	 *        the ID of the process
	 * @return the physical memory size of the process in kilobytes or <code>-1</code> if the memory size could not be
	 *         determined
	 */
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

	/**
	 * Returns the maximum amount of memory in kilobytes that can be used for memory management.
	 * 
	 * @return the maximum amount of memory in kilobytes that can be used for memory management or <code>-1</code> if
	 *         the value could not be determined
	 */
	public static int getMaximumUsableMemory() {

		final long maxHeap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax();
		if (maxHeap == -1L) {
			return -1;
		}

		return (int) (maxHeap / 1024L);
	}

	/**
	 * Dumps the current memory utilization, broken down into the different memory pools, to stdout.
	 */
	public static void dumpMemoryUtilization() {

		System.out.println("-------- Memory Pools --------");
		final List<MemoryPoolMXBean> mpList = ManagementFactory.getMemoryPoolMXBeans();
		final Iterator<MemoryPoolMXBean> mpIt = mpList.iterator();
		final StringBuilder sb = new StringBuilder();
		while (mpIt.hasNext()) {
			final MemoryPoolMXBean mp = mpIt.next();
			sb.append(mp.getName());
			sb.append(" (");
			sb.append(mp.getType());
			sb.append(") :\t");

			final MemoryUsage mu = mp.getUsage();
			final long init = mu.getInit();
			final long used = mu.getUsed();
			final long committed = mu.getCommitted();
			final long max = mu.getMax();

			sb.append(byteToMegaByte(init));
			sb.append(" MB init,\t");
			sb.append(byteToMegaByte(used));
			sb.append(" MB used,\t");
			sb.append(byteToMegaByte(committed));
			sb.append(" MB committed,\t");
			sb.append(byteToMegaByte(max));
			sb.append(" MB max\n");
		}
		System.out.println(sb.toString());
	}

	/**
	 * Returns the value of the MaxHeapFreeRatio parameter passed to the JVM at startup.
	 * 
	 * @return the value of MaxHeapFreeRatio parameter or <code>-1</code> if no such parameter exists
	 */
	public static int getMaxHeapFreeRatio() {

		final String val = getValueOfJVMParameter(JVM_MAX_HEAP_FREE_RATIO);
		if (val == null) {
			return -1;
		}

		return Integer.parseInt(val);
	}

	/**
	 * Returns the value of the MinHeapFreeRatio parameter passed to the JVM at startup.
	 * 
	 * @return the value of MinHeapFreeRatio parameter or <code>-1</code> if no such parameter exists
	 */
	public static int getMinHeapFreeRatio() {

		final String val = getValueOfJVMParameter(JVM_MIN_HEAP_FREE_RATIO);
		if (val == null) {
			return -1;
		}

		return Integer.parseInt(val);

	}

	/**
	 * Auxiliary method to parse the arguments passed to JVM at startup and extract values from these arguments by means
	 * of a regular expression.
	 * 
	 * @param parameter
	 *        the regular expression to apply to the arguments
	 * @return the first value extracted from the first matching argument
	 */
	private static String getValueOfJVMParameter(final Pattern parameter) {

		final RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
		final Iterator<String> it = runtimeMXBean.getInputArguments().iterator();
		while (it.hasNext()) {
			final Matcher matcher = parameter.matcher(it.next());
			if (matcher.matches()) {
				return matcher.group(1);
			}
		}

		return null;
	}

	/**
	 * Checks if the JVM meets all requirements to work with the memory negotiator service. If any requirement is not
	 * met a runtime exception is thrown.
	 */
	public static void checkCompatibility() {

		final RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
		if (runtimeMXBean == null) {
			throw new RuntimeException(
				"Your JVM does not support the RuntimeMXBean which is required by the memory negotiation service");
		}

		// Make sure we are running on a HotSpot JVM
		if (!REQUIRED_SPEC_VENDOR.equals(runtimeMXBean.getSpecVendor())) {
			throw new RuntimeException("Please make sure you execute this program on a Sun/Oracle HotSpot JVM");
		}

		if (getMaximumUsableMemory() == -1) {
			throw new RuntimeException(
				"Cannot determine the maximum amount of memory that is available for memory management");
		}

		// Check correctness with regard to operating system interaction
		final int pid = getPID();
		if (pid == -1) {
			throw new RuntimeException("The JVM is unable to determine its operating system process ID");
		}
	}

	/**
	 * Auxiliary method to convert bytes into megabytes.
	 * 
	 * @param val
	 *        the value in bytes
	 * @return the value converted from bytes to megabytes
	 */
	private static int byteToMegaByte(final long val) {

		return (int) (val / 1048576L);
	}
}
