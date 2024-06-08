package io.github.mrbubble

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.SynchronousQueue
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ItemLockerTest {
    @Test
    fun `ItemLocker guarantees mutual exclusion for the same item`() {
        val output = ConcurrentLinkedQueue<Pair<Int, Int>>()
        val locker = ItemLocker<String>()
        val threads = buildList {
            for (i in 1..100) {
                add(thread {
                    locker.locked("lock") {
                        for (j in 1..5) {
                            output.add(i to j)
                            Thread.sleep(1)
                        }
                    }
                })
            }
        }
        threads.forEach { it.join() }
        var previousThread: Int? = null
        var previousValue: Int? = null

        for ((thread, value) in output) {
            if (previousThread == null) {
                previousThread = thread
                previousValue = value
                continue
            }
            // Ensure that each thread places all of its 5 values in sequence before switching to a different thread.
            if (previousValue!! < value) {
                assertEquals(
                    previousValue + 1,
                    value,
                    "went from ($previousThread, $previousValue) to ($thread, $value)"
                )
                assertEquals(
                    previousThread, thread,
                    "went from ($previousThread, $previousValue) to ($thread, $value)"
                )
            } else {
                assertNotEquals(
                    previousThread,
                    thread,
                    "went from ($previousThread, $previousValue) to ($thread, $value)"
                )
                assertEquals(5, previousValue, "went from ($previousThread, $previousValue) to ($thread, $value)")
                assertEquals(1, value, "went from ($previousThread, $previousValue) to ($thread, $value)")
            }
            previousThread = thread
            previousValue = value
        }
    }

    @Test
    fun `ItemLocker allows concurrent execution of different items`() {
        val locker = ItemLocker<String>()
        val queue = SynchronousQueue<Int>()
        val producer = thread {
            locker.locked("producer") {
                for (i in 1..100) {
                    queue.put(i)
                }
                queue.put(0)
            }
        }

        val output = ConcurrentLinkedQueue<Int>()

        val consumer = thread {
            locker.locked("consumer") {
                while (true) {
                    val i = queue.take()
                    if (i == 0) {
                        break
                    }
                    output.add(i)
                }
            }
        }
        producer.join()
        consumer.join()
        assertEquals((1..100).toList(), output.toList())
    }

    @Test
    fun `ItemLocker is reentrant`() {
        val locker = ItemLocker<String>()
        val output = buildList {
            add(1)
            locker.locked("item") {
                add(2)
                locker.locked("item") {
                    add(3)
                }
                add(4)
            }
            add(5)
        }

        assertEquals((1..5).toList(), output)
    }

    @Test
    fun `unlocking without locking throws exception`() {
        val locker = ItemLocker<String>()
        assertThrows<IllegalStateException> {
            locker.unlock("item")
        }
    }

    @Test
    fun `unlocking from different thread throws exception`() {
        val locker = ItemLocker<String>()
        val t1 = thread {
            locker.lock("item")
        }
        t1.join()
        val t2 = thread {
            assertThrows<IllegalStateException> {
                locker.unlock("item")
            }
        }
        t2.join()
    }

}