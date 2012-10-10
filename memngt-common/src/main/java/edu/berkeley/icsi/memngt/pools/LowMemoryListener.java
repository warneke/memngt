package edu.berkeley.icsi.memngt.pools;

/**
 * This interface must be implemented to receive notifications about low memory resources.
 * 
 * @author warneke
 */
public interface LowMemoryListener {

	/**
	 * Indicates that the memory available in the pool has fallen below the specified threshold.
	 * 
	 * @param availableMemory
	 *        the memory still available in the pool in kilobytes.
	 */
	void indicateLowMemory(int availableMemory);
}
