package org.afterlike.catdueller.core

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

object DelayedTaskHandler {

    private val tasks = mutableListOf<DelayedTask>()

    fun schedule(ticks: Int, block: () -> Unit) {
        tasks.add(DelayedTask(ticks, block))
    }

    @SubscribeEvent
    fun onTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.START) return

        val iterator = tasks.iterator()
        while (iterator.hasNext()) {
            val task = iterator.next()
            if (task.tick()) {
                iterator.remove()
            }
        }
    }
}
