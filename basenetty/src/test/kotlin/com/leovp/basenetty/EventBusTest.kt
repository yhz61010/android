package com.leovp.basenetty

import com.leovp.basenetty.eventbus.base.EventBusAttributes
import com.leovp.basenetty.eventbus.handler.EventBusHandler
import com.leovp.basenetty.eventbus.util.EventBus
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class EventBusTest {

    @AfterEach
    fun tearDown() {
        EventBus.clearAllHandlers()
    }

    @Test
    fun `register and processHandlers invokes handler`() {
        val invoked = AtomicInteger(0)
        val handler = EventBusHandler { invoked.incrementAndGet() }
        EventBus.register("addr1", handler = handler)
        EventBus.processHandlers("addr1") { _, h -> h.handle(null) }
        invoked.get().shouldBeEqualTo(1)
    }

    @Test
    fun `unregister removes handler`() {
        val invoked = AtomicInteger(0)
        EventBus.register("addr2", handler = EventBusHandler { invoked.incrementAndGet() })
        EventBus.unregister("addr2")
        EventBus.processHandlers("addr2") { _, h -> h.handle(null) }
        invoked.get().shouldBeEqualTo(0)
    }

    @Test
    fun `clearAllHandlers clears both handlers and reply handlers`() {
        val invoked = AtomicInteger(0)
        EventBus.register("addr3", handler = EventBusHandler { invoked.incrementAndGet() })
        EventBus.send("addr4", handler = EventBusHandler { invoked.incrementAndGet() })
        EventBus.clearAllHandlers()
        EventBus.processHandlers("addr3") { _, h -> h.handle(null) }
        invoked.get().shouldBeEqualTo(0)
    }

    @Test
    fun `processReplyHandler removes handler after use`() {
        val invoked = AtomicInteger(0)
        val result = EventBus.send("addr5", handler = EventBusHandler { invoked.incrementAndGet() })
        val replyAddr = result[EventBusAttributes.REPLY_ADDRESS] as String
        EventBus.processReplyHandler(replyAddr) { h -> h.handle(null) }
        invoked.get().shouldBeEqualTo(1)
        // Second call should not invoke
        EventBus.processReplyHandler(replyAddr) { h -> h.handle(null) }
        invoked.get().shouldBeEqualTo(1)
    }

    @Test
    fun `concurrent register does not lose handlers`() {
        val threadCount = 10
        val barrier = CyclicBarrier(threadCount)
        val latch = CountDownLatch(threadCount)
        val address = "concurrent-addr"

        val threads = (0 until threadCount).map {
            Thread {
                barrier.await()
                EventBus.register(address, handler = EventBusHandler { })
                latch.countDown()
            }
        }
        threads.forEach { it.start() }
        latch.await()

        val count = AtomicInteger(0)
        EventBus.processHandlers(address) { _, _ -> count.incrementAndGet() }
        count.get().shouldBeEqualTo(threadCount)
    }

    @Test
    fun `send without handler does not add reply address`() {
        val result = EventBus.send("addr6", message = "hello")
        (EventBusAttributes.REPLY_ADDRESS !in result).shouldBeTrue()
    }
}
