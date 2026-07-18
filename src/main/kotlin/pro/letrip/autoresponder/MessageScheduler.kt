package pro.letrip.autoresponder

import net.minecraft.client.Minecraft
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.random.Random

/**
 * File d'envoi differe. Les messages sont pousses depuis le thread reseau puis
 * envoyes sur le thread client au tick, une fois le delai ecoule.
 */
object MessageScheduler {

    private data class Pending(val sendAt: Long, val message: String, val isCommand: Boolean)

    private val queue = ConcurrentLinkedQueue<Pending>()

    fun submit(response: Response) {
        val delay = if (response.maxDelayMs <= response.minDelayMs) {
            response.minDelayMs
        } else {
            Random.nextLong(response.minDelayMs, response.maxDelayMs + 1)
        }
        queue.add(Pending(System.currentTimeMillis() + delay, response.message, response.isCommand))
    }

    fun tick(client: Minecraft) {
        val connection = client.player?.connection ?: return
        val now = System.currentTimeMillis()
        val iterator = queue.iterator()
        while (iterator.hasNext()) {
            val pending = iterator.next()
            if (now < pending.sendAt) continue

            if (pending.isCommand) {
                connection.sendCommand(pending.message)
            } else {
                connection.sendChat(pending.message)
            }
            iterator.remove()
        }
    }

    fun clear() = queue.clear()
}
