package best.spaghetcodes.catdueller.core

class DelayedTask(private var ticks: Int, private val runnable: () -> Unit) {

    fun tick(): Boolean {
        if (ticks-- <= 0) {
            runnable()
            return true
        }
        return false
    }
}
