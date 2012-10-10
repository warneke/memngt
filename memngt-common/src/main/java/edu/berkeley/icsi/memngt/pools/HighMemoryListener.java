package edu.berkeley.icsi.memngt.pools;

/**
 * This interface must be implemented to receive notifications about high memory resources.
 * 
 * @author warneke
 */
public interface HighMemoryListener {

	/**
	 * Indicates that the memory available in the pool has risen above the specified threshold.
	 * 
	 * @param availableMemory
	 *        the memory available in the pool in kilobytes.
	 */
	void indicateHighMemory(int availableMemory);
}
