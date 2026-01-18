package org.afterlike.catdueller.bot.player

import org.afterlike.catdueller.CatDueller

/**
 * Manages player inventory operations including item selection and item detection.
 * Provides utilities for switching between hotbar slots based on item name or metadata.
 */
object Inventory {

    /**
     * Selects a hotbar item by searching for a matching item name.
     * Searches slots 0-8 (the hotbar) for an item whose unlocalized name contains the search string.
     *
     * @param item The item name substring to search for (case-insensitive).
     * @return True if the item was found and selected, false otherwise.
     */
    fun setInvItem(item: String): Boolean {
        val _item = item.lowercase()
        if (CatDueller.mc.thePlayer != null && CatDueller.mc.thePlayer.inventory != null) {
            for (i in 0..8) {
                val stack = CatDueller.mc.thePlayer.inventory.getStackInSlot(i)
                if (stack != null && stack.unlocalizedName.lowercase().contains(_item)) {
                    CatDueller.mc.thePlayer.inventory.currentItem = i
                    return true
                }
            }
        }
        return false
    }

    /**
     * Selects a hotbar item by matching its damage value (metadata).
     * Useful for differentiating items like potions that share the same base item ID
     * but have different damage values indicating different potion types.
     *
     * @param itemDamage The item damage/metadata value to match.
     * @return True if the item was found and selected, false otherwise.
     */
    fun setInvItemByDamage(itemDamage: Int): Boolean {
        if (CatDueller.mc.thePlayer != null && CatDueller.mc.thePlayer.inventory != null) {
            for (i in 0..8) {
                val stack = CatDueller.mc.thePlayer.inventory.getStackInSlot(i)
                if (stack != null && stack.itemDamage == itemDamage) {
                    CatDueller.mc.thePlayer.inventory.currentItem = i
                    return true
                }
            }
        }
        return false
    }
}
