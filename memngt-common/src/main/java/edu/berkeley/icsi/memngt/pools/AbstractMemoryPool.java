package edu.berkeley.icsi.memngt.pools;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.esotericsoftware.minlog.Log;

import edu.berkeley.icsi.memngt.utils.ClientUtils;

public abstract class AbstractMemoryPool<T> {

	private static final int ADAPTATION_GRANULARITY = 4 * 1024;

	private final String name;

	private final int pid;

	private final int bufferSize;

	private final BlockingQueue<T> buffers;

	private final Object adjustmentLock = new Object();

	/**
	 * The total amount of memory allocated by this pool in kilobytes.
	 */
	private final AtomicInteger allocatedMemory = new AtomicInteger(0);

	/**
	 * The amount of memory still available in the memory pool.
	 */
	private final AtomicInteger availableMemory = new AtomicInteger(0);

	private volatile int lowMemoryThreshold = -1;

	private volatile LowMemoryListener lowMemoryListener = null;

	private final AtomicBoolean lowMemoryNotificationSent = new AtomicBoolean(false);

	private volatile int highMemoryThreshold = Integer.MAX_VALUE;

	private volatile HighMemoryListener highMemoryListener = null;

	private final AtomicBoolean highMemoryNotificationSent = new AtomicBoolean(false);

	/**
	 * The granted memory size of the application containing the memory pool in kilobytes.
	 */
	private final AtomicInteger grantedMemorySize = new AtomicInteger(0);

	/**
	 * The maximum fraction of the heap that can be used before it is resized by the JVM.
	 */
	private final float heapResizeLimit;

	protected AbstractMemoryPool(final String name, final int initialCapacity, final int bufferSize) {
		this.name = name;
		this.pid = ClientUtils.getPID();
		this.bufferSize = bufferSize;
		this.buffers = new ArrayBlockingQueue<T>(initialCapacity);
		this.heapResizeLimit = (100 - ClientUtils.getMinHeapFreeRatio()) / 100.0f;
	}

	protected AbstractMemoryPool(final int initialCapacity, final int bufferSize) {
		final int pid = ClientUtils.getPID();
		this.name = getDefaultName(pid);
		this.pid = pid;
		this.bufferSize = bufferSize;
		this.buffers = new ArrayBlockingQueue<T>(initialCapacity);
		this.heapResizeLimit = (100 - ClientUtils.getMinHeapFreeRatio()) / 100.0f;
	}

	/**
	 * Returns the default name for this memory pool.
	 * 
	 * @param pid
	 *        the ID of the process containing this memory pool
	 * @return the default name for this memory pool
	 */
	private static String getDefaultName(final int pid) {
		return "MemoryPool " + pid;
	}

	/**
	 * Returns the granted memory size in kilobytes.
	 * 
	 * @return the granted memory size in kilobytes
	 */
	public int getGrantedMemorySize() {
		return this.grantedMemorySize.get();
	}

	/**
	 * Returns the amount of available memory in kilobytes.
	 * 
	 * @return the amount of available memory in kilobytes
	 */
	public int getAvailableMemory() {
		return this.availableMemory.get();
	}

	/**
	 * Clears the memory pool and resets all internal variables.
	 */
	public void clear() {
		this.buffers.clear();
		this.allocatedMemory.set(0);
		this.availableMemory.set(0);
		this.grantedMemorySize.set(0);
		this.lowMemoryThreshold = -1;
		this.lowMemoryListener = null;
		this.lowMemoryNotificationSent.set(false);
		this.highMemoryThreshold = Integer.MAX_VALUE;
		this.highMemoryListener = null;
		this.highMemoryNotificationSent.set(false);
	}

	/**
	 * Returns the number of available buffers in the memory pool.
	 * 
	 * @return the number of available buffers in the memory pool
	 */
	public int size() {
		return this.buffers.size();
	}

	/**
	 * Returns the size of a single buffer from this pool in bytes;
	 * 
	 * @return the size of a single buffer from this pool in bytes
	 */
	public int getBufferSize() {
		return this.bufferSize * 1024;
	}

	/**
	 * Sets the low memory listener for this memory pool. The listener is notified when the amount of memory available
	 * in this pool falls below the specified threshold.
	 * 
	 * @param lowMemoryThreshold
	 *        the memory threshold in kilobytes
	 * @param lowMemoryListener
	 *        the low memory listener to register
	 */
	public void setLowMemoryListener(final int lowMemoryThreshold, final LowMemoryListener lowMemoryListener) {

		this.lowMemoryListener = lowMemoryListener;
		this.lowMemoryThreshold = lowMemoryThreshold;
		this.lowMemoryNotificationSent.set(false);
	}

	/**
	 * Sets the high memory listener for this memory pool. The listener is notified when the amount of memory available
	 * in this pool rises above the specified threshold.
	 * 
	 * @param highMemoryThreshold
	 *        the memory threshold in kilobytes
	 * @param highMemoryListener
	 *        the high memory listener to register
	 */
	public void setHighMemoryListener(final int highMemoryThreshold, final HighMemoryListener highMemoryListener) {

		this.highMemoryListener = highMemoryListener;
		this.highMemoryThreshold = highMemoryThreshold;
		this.highMemoryNotificationSent.set(false);
	}

	/**
	 * Adjusts the memory pool to the granted memory size.
	 */
	public void increaseGrantedShareAndAdjust(final int delta) {

		if (delta == 0) {
			return;
		}

		final int sizeOfBuffer = this.bufferSize;

		final long start = System.currentTimeMillis();

		final int grantedMemoryShare = this.grantedMemorySize.addAndGet(delta);
		if (grantedMemoryShare < 0) {
			throw new IllegalStateException("grantedMemoryShare is" + grantedMemoryShare);
		}

		final int reducedGrantedMemorySize = (int) ((float) grantedMemoryShare * this.heapResizeLimit);

		Log.info("Granted memory share is now " + grantedMemoryShare + " kilobytes (reduced "
			+ reducedGrantedMemorySize + " kilobytes), adjusting...");

		int allocatedBuffers = 0, releasedBuffers = 0;

		synchronized (this.adjustmentLock) {

			// Allocate memory until the reduced granted memory size is crossed, check every ADAPTATION_GRANULARITY
			// kilobytes

			int kilobytesUntilNextCheck = 0;
			while (true) {

				if (kilobytesUntilNextCheck <= 0) {
					if (ClientUtils.getPhysicalMemorySize(this.pid) > reducedGrantedMemorySize) {
						break;
					}
					kilobytesUntilNextCheck = ADAPTATION_GRANULARITY;
				}

				returnBufferInternal(allocatedNewBuffer(), false);
				this.allocatedMemory.addAndGet(sizeOfBuffer);
				kilobytesUntilNextCheck -= sizeOfBuffer;
				++allocatedBuffers;
			}

			// Check if we have exceed the granted memory share with our previous allocations
			int excessMemory = ClientUtils.getPhysicalMemorySize(this.pid) - grantedMemoryShare;

			if (excessMemory > 0) {

				while (excessMemory > 0) {

					if (requestBufferInternal(false) == null) {
						Log.error(this.name + ": No more buffers to release");
						break;
					}

					this.allocatedMemory.addAndGet(-sizeOfBuffer);
					excessMemory -= sizeOfBuffer;
					kilobytesUntilNextCheck -= sizeOfBuffer;
					++releasedBuffers;

				}
				Log.info("Suggesting garbage collection");
				System.gc();
			}

			// If we still consume too much memory, further release buffers and call System.gc every
			// ADAPTATION_GRUNLARITY kilobytes
			kilobytesUntilNextCheck = 0;
			while (true) {

				if (kilobytesUntilNextCheck <= 0) {
					excessMemory = ClientUtils.getPhysicalMemorySize(this.pid) - grantedMemoryShare;
					if (excessMemory <= 0) {
						break;
					}

					Log.info("Suggesting garbage collection");
					System.gc();
					kilobytesUntilNextCheck = ADAPTATION_GRANULARITY;
				}

				if (requestBufferInternal(false) == null) {
					Log.error(this.name + ": No more buffers to release");
					break;
				}

				this.allocatedMemory.addAndGet(-sizeOfBuffer);
				kilobytesUntilNextCheck -= sizeOfBuffer;
				++releasedBuffers;
			}
		}

		// Construct the debug message
		if (Log.INFO) {
			final StringBuilder sb = new StringBuilder();
			sb.append(this.name);
			sb.append(": Allocated ");
			sb.append(allocatedBuffers);
			sb.append(" buffers, ");
			sb.append(" released ");
			sb.append(releasedBuffers);
			sb.append(" buffers, ");
			sb.append(this.buffers.size());
			sb.append(" buffers available (operation took ");
			sb.append(System.currentTimeMillis() - start);
			sb.append(" ms)");

			Log.info(sb.toString());
		}

		checkThresholds(this.availableMemory.get());
	}

	private void checkThresholds(final int availableMemory) {

		if (availableMemory > this.lowMemoryThreshold) {
			this.lowMemoryNotificationSent.set(false);
		} else if (availableMemory < this.highMemoryThreshold) {
			this.highMemoryNotificationSent.set(false);
		}

		if (availableMemory < this.lowMemoryThreshold) {
			if (this.lowMemoryNotificationSent.compareAndSet(false, true)) {
				this.lowMemoryListener.indicateLowMemory(availableMemory);
			}
		} else if (availableMemory > this.highMemoryThreshold) {
			if (this.highMemoryNotificationSent.compareAndSet(false, true)) {
				this.highMemoryListener.indicateHighMemory(availableMemory);
			}
		}
	}

	private T requestBufferInternal(final boolean checkThreshold) {

		int newAvailableMemory;
		while (true) {

			final int availableMemory = this.availableMemory.get();
			if (availableMemory == 0) {
				return null;
			}

			newAvailableMemory = availableMemory - this.bufferSize;
			if (this.availableMemory.compareAndSet(availableMemory, newAvailableMemory)) {
				break;
			}

			// We had a race, try again
		}

		final T buffer = this.buffers.poll();
		if (buffer == null) {
			throw new IllegalStateException("There must be a race condition somewhere");
		}

		if (checkThreshold) {

			if (newAvailableMemory < this.highMemoryThreshold) {
				this.highMemoryNotificationSent.set(false);
			}

			if (newAvailableMemory < this.lowMemoryThreshold) {
				if (this.lowMemoryNotificationSent.compareAndSet(false, true)) {
					this.lowMemoryListener.indicateLowMemory(newAvailableMemory);
				}
			}
		}

		return buffer;
	}

	/**
	 * Requests a buffer from the memory pool.
	 * 
	 * @return a buffer from the memory pool or <code>null</code> if no buffer is available
	 */
	public T requestBuffer() {
		return requestBufferInternal(true);
	}

	private void returnBufferInternal(final T buffer, final boolean checkThreshold) {

		this.buffers.add(buffer);
		final int availableMemory = this.availableMemory.addAndGet(this.bufferSize);

		if (availableMemory <= 0) {
			throw new IllegalStateException("There must be a race condition somewhere");
		}

		if (availableMemory > this.lowMemoryThreshold) {
			this.lowMemoryNotificationSent.set(false);
		}

		if (availableMemory > this.highMemoryThreshold) {
			if (this.highMemoryNotificationSent.compareAndSet(false, true)) {
				this.highMemoryListener.indicateHighMemory(availableMemory);
			}
		}
	}

	/**
	 * Returns a previously removed buffer back to the memory pool.
	 * 
	 * @param buffer
	 *        the buffer to return to the pool
	 */
	public void returnBuffer(final T buffer) {
		returnBufferInternal(buffer, true);
	}

	public int relinquishMemory(final int minimumAmountToRelinquish, final int minimumAmountToPreserve) {

		int newAvailableMemory;
		while (true) {

			final int availableMemory = this.availableMemory.get();

			int amountToRelinquish = availableMemory - minimumAmountToPreserve;
			// Make sure amount to relinquish is a multiple of the buffer size
			amountToRelinquish = (amountToRelinquish / this.bufferSize) * this.bufferSize;

			if (amountToRelinquish < minimumAmountToRelinquish) {
				return 0;
			}

			newAvailableMemory = availableMemory - amountToRelinquish;
			if (!this.availableMemory.compareAndSet(availableMemory, newAvailableMemory)) {
				// We had a race, try again
				continue;
			}

			int i = 0;
			while (i < amountToRelinquish) {
				this.buffers.poll();
				i += this.bufferSize;
			}

			checkThresholds(newAvailableMemory);

			return amountToRelinquish;
		}
	}

	/**
	 * Allocates a new buffer to be added to the memory pool.
	 * 
	 * @return the newly allocated buffer
	 */
	protected abstract T allocatedNewBuffer();
}
