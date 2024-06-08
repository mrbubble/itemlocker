package io.github.mrbubble

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


/**
 * A class that provides a locking mechanism for individual items of type [T].
 *
 * @param T the type of items to be locked
 */
class ItemLocker<T> {
    private val innerLock = ReentrantLock()
    private val condition = innerLock.newCondition()
    private val lockedItems = mutableMapOf<T, ThreadRefCounter>()

    private class ThreadRefCounter(val thread: Thread) {
        private var refCounter = 1
        fun inc() {
            refCounter++
        }

        fun dec(): Boolean {
            refCounter--
            return refCounter <= 0
        }
    }

    /**
     * Locks the specified [item]. If the item is already locked by another thread,
     * the current thread will wait until the item becomes available. If the current
     * thread already holds the lock on the item, it can re-enter without blocking.
     *
     * @param item the item to lock
     */
    fun lock(item: T) {
        innerLock.withLock {
            val refCounter = lockedItems[item]
            if (refCounter?.thread == Thread.currentThread()) {
                refCounter!!.inc()
                return
            }
            while (lockedItems.contains(item)) {
                condition.await()
            }
            lockedItems[item] = ThreadRefCounter(Thread.currentThread())
        }
    }

    /**
     * Unlocks the specified [item]. This method should only be called by the thread
     * that holds the lock on the item. If the current thread has re-entered the lock
     * multiple times, it will need to call unlock an equal number of times to fully
     * release the lock.
     *
     * @param item the item to unlock
     */
    fun unlock(item: T) {
        innerLock.withLock {
            val refCounter = lockedItems[item]
            check(refCounter?.thread == Thread.currentThread()) {
                "Current thread does not hold the lock on this item."
            }
            if (refCounter!!.dec()) {
                lockedItems.remove(item)
                condition.signalAll()
            }
        }
    }
}

/**
 * Executes the given [block] of code while holding the lock on the specified [item].
 * The lock will be acquired before executing the block and released after the block
 * has been executed.
 *
 * @param T the type of the item to be locked
 * @param V the return type of the block
 * @param item the item to lock
 * @param block the code to execute while holding the lock
 * @return the result of the block
 */
fun <T, V> ItemLocker<T>.locked(item: T, block: () -> V): V {
    lock(item)
    try {
        return block()
    } finally {
        unlock(item)
    }
}