package best.spaghetcodes.catdueller.utils.game

import net.minecraft.entity.Entity
import net.minecraft.util.Vec3

/**
 * Extension functions for Minecraft classes.
 *
 * Provides additional utility methods for Vec3 and Entity classes
 * to simplify common vector and entity operations.
 */
object ExtensionUtil {

    /**
     * Scales all components of this vector by the given factor.
     *
     * @param x The scaling factor to apply to all components.
     * @return A new Vec3 with all components multiplied by the scaling factor.
     */
    fun Vec3.scale(x: Double): Vec3 {
        return Vec3(this.xCoord * x, this.yCoord * x, this.zCoord * x)
    }

    /**
     * Calculates the velocity of this entity based on position delta.
     *
     * Computes velocity by calculating the difference between current and
     * previous tick positions, providing more reliable results than the
     * built-in motion properties.
     *
     * @return A Vec3 representing the entity's velocity per tick.
     */
    fun Entity.getVelocity(): Vec3 {
        return Vec3(this.posX - this.prevPosX, this.posY - this.prevPosY, this.posZ - this.prevPosZ)
    }

}