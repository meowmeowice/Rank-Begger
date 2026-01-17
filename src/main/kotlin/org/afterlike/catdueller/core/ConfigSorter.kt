package org.afterlike.catdueller.core

import gg.essential.vigilance.data.Category
import gg.essential.vigilance.data.SortingBehavior

/**
 * Custom sorting behavior for configuration categories in the settings GUI.
 *
 * Defines the display order of configuration categories to ensure
 * a logical and user-friendly arrangement in the settings menu.
 */
class ConfigSorter : SortingBehavior() {

    /**
     * Ordered list of category names defining their display sequence.
     * Categories not in this list will appear after all listed categories.
     */
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

    /**
     * Returns a comparator that sorts categories based on their position in the items list.
     *
     * @return A comparator for ordering Category objects by their predefined index.
     */
    override fun getCategoryComparator(): Comparator<in Category> = compareBy { items.indexOf(it.name) }
}
