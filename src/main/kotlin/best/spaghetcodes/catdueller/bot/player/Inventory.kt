package best.spaghetcodes.catdueller.bot.player

import best.spaghetcodes.catdueller.CatDueller

object Inventory {

    /**
     * Sets the players current item to the item passed
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
     * Set the current inventory item (by itemDamage, use for potions etc)
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

    /**
     * Move the the passed inv slot
     */
    fun setInvSlot(slot: Int) {
        if (CatDueller.mc.thePlayer != null && CatDueller.mc.thePlayer.inventory != null) {
            CatDueller.mc.thePlayer.inventory.currentItem = slot
        }
        // bruh
    }

    /**
     * Checks it the player has this item in their inventory
     */
    fun hasItem(item: String): Boolean {
        val _item = item.lowercase()
        if (CatDueller.mc.thePlayer != null) {
            for (itemStack in CatDueller.mc.thePlayer.getInventory()) {
                if (itemStack.unlocalizedName.lowercase().contains(_item)) {
                    return true
                }
            }
        }
        return false
    }

}
