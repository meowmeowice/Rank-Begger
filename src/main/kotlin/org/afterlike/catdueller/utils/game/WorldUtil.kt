package org.afterlike.catdueller.utils.game

import net.minecraft.block.Block
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Blocks
import net.minecraft.util.BlockPos
import net.minecraft.util.Vec3
import org.afterlike.catdueller.CatDueller
import org.afterlike.catdueller.utils.game.ExtensionUtil.getVelocity
import org.afterlike.catdueller.utils.game.ExtensionUtil.scale

/**
 * Utility object for world and terrain analysis.
 *
 * Provides functions to detect blocks, edges, and air gaps relative to player
 * position and orientation. Useful for navigation, edge detection, and
 * environmental awareness in dueling scenarios.
 */
object WorldUtil {

    /**
     * Gets the block at a specified distance in front of the player.
     *
     * @param player The player whose facing direction is used.
     * @param distance The distance in blocks to check ahead.
     * @param yMod Vertical offset from feet level. 0 = feet, 1 = one block above feet, etc.
     * @return The [Block] at the specified position.
     */
    fun blockInFront(
        player: EntityPlayer,
        distance: Float,
        yMod: Float = 0f
    ): Block {
        val vec = Vec3(player.lookVec.xCoord * distance, 0.0, player.lookVec.zCoord * distance)
        return CatDueller.mc.theWorld.getBlockState(player.position.add(vec.xCoord, -0.2 + yMod, vec.zCoord)).block
    }

    /**
     * Gets the block at the player's projected position after a number of ticks.
     *
     * Calculates where the player will be based on current velocity and returns
     * the block at that position.
     *
     * @param player The player whose velocity is used for projection.
     * @param ticks The number of ticks to project ahead.
     * @param yMod Vertical offset from feet level.
     * @return The [Block] at the projected position.
     */
    fun blockInPath(player: EntityPlayer, ticks: Int, yMod: Float = 0f): Block {
        val velo = player.getVelocity()
        val vec = Vec3(velo.xCoord * ticks, 0.0, velo.zCoord * ticks)
        return CatDueller.mc.theWorld.getBlockState(player.position.add(vec.xCoord, -0.2 + yMod, vec.zCoord)).block
    }

    /**
     * Checks if there is an air gap (void/edge) in front of the player.
     *
     * @param player The player to check from.
     * @param distance The maximum distance in blocks to check.
     * @return `true` if an air gap is detected within the distance, `false` otherwise.
     */
    fun airInFront(player: EntityPlayer, distance: Float): Boolean {
        return airCheck(player.position, distance, EntityUtil.get2dLookVec(player))
    }

    /**
     * Checks if there is an air gap (void/edge) behind the player.
     *
     * @param player The player to check from.
     * @param distance The maximum distance in blocks to check.
     * @return `true` if an air gap is detected within the distance, `false` otherwise.
     */
    fun airInBack(player: EntityPlayer, distance: Float): Boolean {
        return airCheck(player.position, distance, EntityUtil.get2dLookVec(player).rotateYaw(180f))
    }

    /**
     * Checks if there is a solid wall behind the player within the given distance.
     *
     * @param player The player to check from.
     * @param distance The maximum distance in blocks to check behind.
     * @return `true` if a solid (non-air) block is found behind the player within the distance.
     */
    fun wallBehind(player: EntityPlayer, distance: Int): Boolean {
        val behindVec = EntityUtil.get2dLookVec(player).rotateYaw(180f)
        for (i in 1..distance) {
            val x = player.posX + behindVec.xCoord * i
            val z = player.posZ + behindVec.zCoord * i
            val blockFeet = CatDueller.mc.theWorld.getBlockState(BlockPos(x, player.posY, z)).block
            val blockHead = CatDueller.mc.theWorld.getBlockState(BlockPos(x, player.posY + 1.0, z)).block
            if (blockFeet != Blocks.air || blockHead != Blocks.air) {
                return true
            }
        }
        return false
    }

    /**
     * Checks if there is an air gap (void/edge) to the left of the player.
     *
     * @param player The player to check from.
     * @param distance The maximum distance in blocks to check.
     * @return `true` if an air gap is detected within the distance, `false` otherwise.
     */
    fun airOnLeft(player: EntityPlayer, distance: Float): Boolean {
        return airCheck(player.position, distance, EntityUtil.get2dLookVec(player).rotateYaw(90f))
    }

    /**
     * Checks if there is an air gap (void/edge) to the right of the player.
     *
     * @param player The player to check from.
     * @param distance The maximum distance in blocks to check.
     * @return `true` if an air gap is detected within the distance, `false` otherwise.
     */
    fun airOnRight(player: EntityPlayer, distance: Float): Boolean {
        return airCheck(player.position, distance, EntityUtil.get2dLookVec(player).rotateYaw(-90f))
    }

    /**
     * Calculates the distance to the nearest edge on the player's left side.
     *
     * Checks up to 20 blocks to the left for an air gap.
     *
     * @param player The player to check from.
     * @return The distance in blocks to the left edge, or 21.0f if no edge exists within 20 blocks.
     */
    fun distanceToLeftEdge(player: EntityPlayer): Float {
        for (i in 1..20) {
            if (airOnLeft(player, i.toFloat())) {
                return i.toFloat()
            }
        }
        return 21.0f
    }

    /**
     * Calculates the distance to the nearest edge on the player's right side.
     *
     * Checks up to 20 blocks to the right for an air gap.
     *
     * @param player The player to check from.
     * @return The distance in blocks to the right edge, or 21.0f if no edge exists within 20 blocks.
     */
    fun distanceToRightEdge(player: EntityPlayer): Float {
        for (i in 1..20) {
            if (airOnRight(player, i.toFloat())) {
                return i.toFloat()
            }
        }
        return 21.0f
    }

    /**
     * Determines if an entity is falling off an edge into the void.
     *
     * Checks if the player is airborne and has no solid blocks within a 0.6 block
     * radius at 4 blocks below their current position.
     *
     * @param player The player to check.
     * @return `true` if the player is off the edge with no ground below, `false` otherwise.
     */
    fun entityOffEdge(player: EntityPlayer): Boolean {
        if (!player.onGround) {
            val pos = player.positionVector.subtract(Vec3(0.0, 4.0, 0.0))
            val positions = arrayListOf(
                pos.add(Vec3(0.6, 0.0, 0.0)),
                pos.add(Vec3(0.0, 0.0, 0.6)),
                pos.add(Vec3(-0.6, 0.0, 0.0)),
                pos.add(Vec3(0.0, 0.0, -0.6)),
                pos.add(Vec3(0.6, 0.0, 0.6)),
                pos.add(Vec3(-0.6, 0.0, 0.6)),
                pos.add(Vec3(0.6, 0.0, -0.6)),
                pos.add(Vec3(-0.6, 0.0, -0.6))
            )
            for (position in positions) {
                if (CatDueller.mc.theWorld.getBlockState(BlockPos(position)).block != Blocks.air) {
                    return false
                }
            }
            return true
        }
        return false
    }

    /**
     * Determines which lateral direction moves the player closer to a target point.
     *
     * Compares the distance to the target from hypothetical left and right positions
     * to determine optimal strafing direction.
     *
     * @param player The player to calculate from.
     * @param point The target point to approach.
     * @return `true` if moving left gets closer to the point, `false` if moving right is better.
     */
    fun leftOrRightToPoint(player: EntityPlayer, point: Vec3): Boolean {
        val pos = player.positionVector
        val lookVec = EntityUtil.get2dLookVec(player)
        val leftVec = lookVec.rotateYaw(90f).scale(2.0)
        val rightVec = lookVec.rotateYaw(-90f).scale(2.0)

        val leftPos = pos.add(leftVec)
        val rightPos = pos.add(rightVec)

        val leftDist = leftPos.distanceTo(point)
        val rightDist = rightPos.distanceTo(point)

        return leftDist < rightDist
    }

    /**
     * Checks for air gaps along a direction from a starting position.
     *
     * Scans at three Y levels (-0.2, -1.4, -2.2 relative to position) to detect
     * multi-block air gaps that indicate edges or voids.
     *
     * @param pos The starting block position.
     * @param distance The maximum distance to check.
     * @param lookVec The direction vector to check along.
     * @return `true` if all three Y levels are air at any point within the distance.
     */
    private fun airCheck(pos: BlockPos, distance: Float, lookVec: Vec3): Boolean {
        for (i in 1..distance.toInt()) {
            val x = pos.x + lookVec.xCoord * i
            val z = pos.z + lookVec.zCoord * i

            val y1 = pos.y - 0.2
            val y2 = pos.y - 1.4
            val y3 = pos.y - 2.2

            val block1 = CatDueller.mc.theWorld.getBlockState(BlockPos(x, y1, z)).block
            val block2 = CatDueller.mc.theWorld.getBlockState(BlockPos(x, y2, z)).block
            val block3 = CatDueller.mc.theWorld.getBlockState(BlockPos(x, y3, z)).block

            if (block1 == Blocks.air && block2 == Blocks.air && block3 == Blocks.air) {
                return true
            }
        }
        return false
    }
}
