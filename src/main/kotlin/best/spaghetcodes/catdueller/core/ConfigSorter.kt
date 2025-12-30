package best.spaghetcodes.catdueller.core

import gg.essential.vigilance.data.Category
import gg.essential.vigilance.data.SortingBehavior

class ConfigSorter : SortingBehavior() {

    private val items = arrayListOf(
        "General",
        "Combat",
        "Toggling",
        "Sumo",
        "Classic",
        "Queue Dodging",
        "Auto Requeue",
        "AutoGG",
        "Chat Messages",
        "Webhook",
        "Misc",
        "Boosting",
        "Bot Crasher",
        "Debug"
    )

    override fun getCategoryComparator(): Comparator<in Category> = compareBy { items.indexOf(it.name) }

}
