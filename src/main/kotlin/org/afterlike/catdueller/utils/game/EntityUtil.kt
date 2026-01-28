package org.afterlike.catdueller.utils.game

import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.potion.Potion
import net.minecraft.util.MathHelper
import net.minecraft.util.Vec3
import org.afterlike.catdueller.CatDueller
import org.afterlike.catdueller.bot.player.Mouse
import org.afterlike.catdueller.utils.game.ExtensionUtil.getVelocity
import org.afterlike.catdueller.utils.game.ExtensionUtil.scale
import kotlin.math.*

/**
 * Utility object for entity-related calculations and operations.
 *
 * Provides methods for finding opponent entities, calculating rotations needed
 * to aim at targets, computing distances, and determining entity movement directions.
 */
object EntityUtil {

    /**
     * Finds the opponent player entity in the current world.
     *
     * Searches through all player entities in the world and returns the first
     * player that is not the local player and meets targeting criteria.
     *
     * @return The opponent player entity, or null if no valid opponent is found.
     */
    fun getOpponentEntity(): EntityPlayer? {
        if (CatDueller.mc.theWorld != null) {
            for (entity in CatDueller.mc.theWorld.playerEntities) {
                if (entity.displayName != CatDueller.mc.thePlayer.displayName && shouldTarget(entity)) {
                    return entity
                }
            }
        }
        return null
    }

    /**
     * Determines whether a player entity should be targeted.
     *
     * An entity is targetable if it is alive, visible, and within 64 blocks
     * of the local player.
     *
     * @param entity The player entity to evaluate.
     * @return True if the entity should be targeted, false otherwise.
     */
    private fun shouldTarget(entity: EntityPlayer?): Boolean {
        return if (entity == null) {
            false
        } else if (CatDueller.mc.thePlayer.isEntityAlive && entity.isEntityAlive) {
            if (!entity.isInvisible) {
                CatDueller.mc.thePlayer.getDistanceToEntity(entity) <= 64.0f
            } else {
                false
            }
        } else {
            false
        }
    }

    /**
     * Calculates the rotation angles needed to look at a target entity.
     *
     * Supports multiple targeting modes including center-of-entity targeting for projectiles
     * and closest-hitbox-point targeting for melee attacks. Includes prediction logic for
     * projectile weapons (bows and fishing rods) based on distance and target movement.
     *
     * @param player The player entity whose rotations are being calculated.
     * @param target The entity to aim at.
     * @param raw If true, returns the rotation difference; if false, returns absolute rotation values.
     * @param center If true, aims at the entity's eye height; if false, aims at the closest hitbox point.
     * @return A FloatArray containing [yaw, pitch], or null if either entity is null.
     */
    fun getRotations(player: EntityPlayer?, target: Entity?, raw: Boolean, center: Boolean = false): FloatArray? {
        return if (target == null || player == null) {
            null
        } else {
            val pos: Vec3?
            if (center) {
                pos = Vec3(target.posX, target.posY + target.eyeHeight, target.posZ)
            } else {
                if (!Mouse.isUsingProjectile() || (CatDueller.mc.thePlayer?.heldItem?.unlocalizedName?.lowercase()
                        ?.contains("rod") != true && CatDueller.mc.thePlayer?.heldItem?.unlocalizedName?.lowercase()
                        ?.contains("bow") != true)
                ) {
                    val box = target.entityBoundingBox

                    // get the four corners of the hitbox
                    var yPos = player.posY + player.eyeHeight

                    if (!player.onGround) {
                        yPos = target.posY + target.eyeHeight
                    } else if (abs(target.posY - player.posY) > player.eyeHeight) {
                        yPos = target.posY + target.eyeHeight / 2f
                    } else if (player.posY - target.posY > 0.3) {
                        yPos = target.posY + target.eyeHeight
                    }

                    val corner1 = Vec3(box.minX, yPos, box.minZ)
                    val corner2 = Vec3(box.maxX, yPos, box.minZ)
                    val corner3 = Vec3(box.minX, yPos, box.maxZ)
                    val corner4 = Vec3(box.maxX, yPos, box.maxZ)

                    // get the closest 2 corners
                    val closest = getClosestCorner(corner1, corner2, corner3, corner4)
                    var a = closest[0]
                    var b = closest[1]

                    val p = Vec3(player.posX, player.posY + player.eyeHeight, player.posZ)

                    // since the two corners are either always on the same X or same Z position, we don't need complicated math
                    if (a.zCoord == b.zCoord) {
                        if (a.xCoord > b.xCoord) {
                            val temp = a
                            a = b
                            b = temp
                        }
                        pos = if (p.xCoord < a.xCoord) {
                            a
                        } else if (p.xCoord > b.xCoord) {
                            b
                        } else {
                            Vec3(p.xCoord, a.yCoord, a.zCoord)
                        }
                    } else {
                        if (a.zCoord > b.zCoord) {
                            val temp = a
                            a = b
                            b = temp
                        }
                        pos = if (p.zCoord < a.zCoord) {
                            a
                        } else if (p.zCoord > b.zCoord) {
                            b
                        } else {
                            Vec3(a.xCoord, a.yCoord, p.zCoord)
                        }
                    }
                } else {
                    val dist = getDistanceNoY(player, target)

                    // Rod flight speed calculation
                    // Rod initial speed is ~1.5 blocks/tick, but decreases due to air resistance
                    // Average effective speed over distance is approximately 1.4 blocks/tick

                    val baseTicks = when (dist) {
                        in 0f..8f -> dist.toDouble()
                        in 8f..15f -> 15.0
                        in 15f..25f -> 20.0
                        else -> 25.0
                    }

                    // Apply speed effect multiplier if player has speed
                    val speedMultiplier = if (player.isPotionActive(Potion.moveSpeed)) 1.3 else 1.0
                    val adjustedBaseTicks = baseTicks * speedMultiplier

                    // Add ping compensation bonus from config
                    val pingBonus = CatDueller.config?.predictionTicksBonus ?: 0

                    // Apply counter strafe multiplier for bow/rod prediction
                    val counterStrafeMultiplier = CatDueller.bot?.getCounterStrafeMultiplier() ?: 1.0f
                    val basePredictionTicks = adjustedBaseTicks + pingBonus
                    val tickPredict = basePredictionTicks * counterStrafeMultiplier

                    // For bow/rod, use actual velocity but apply prediction compensation
                    val actualVelocity = target.getVelocity()
                    val currentSpeed =
                        sqrt(actualVelocity.xCoord * actualVelocity.xCoord + actualVelocity.zCoord * actualVelocity.zCoord)

                    // Check if we're using bow or rod for different speed calculations
                    val isUsingBow =
                        CatDueller.mc.thePlayer?.heldItem?.unlocalizedName?.lowercase()?.contains("bow") == true
                    val isUsingRod =
                        CatDueller.mc.thePlayer?.heldItem?.unlocalizedName?.lowercase()?.contains("rod") == true

                    val opponentSpeed = if (isUsingBow) {
                        // Bow: Use opponent's actual tracked speed with maximum 0.15 for moving targets (no minimum limit)
                        val trackedSpeed = CatDueller.bot?.opponentActualSpeed ?: 0.13f
                        if (currentSpeed > 0.005) {  // If target is moving (threshold to avoid micro-movements)
                            // Moving target: no minimum, maximum 0.15 speed
                            min(trackedSpeed, 0.15f)
                        } else {
                            // Stationary target: no prediction (0.0 speed)
                            0.0f
                        }
                    } else if (isUsingRod) {
                        // Rod: Use opponent's actual tracked speed with no speed limit
                        val trackedSpeed = CatDueller.bot?.opponentActualSpeed ?: 0.13f
                        if (currentSpeed > 0.005) {  // If target is moving (threshold to avoid micro-movements)
                            // Moving target: use full tracked speed with no limit
                            trackedSpeed
                        } else {
                            // Stationary target: no prediction (0.0 speed)
                            0.0f
                        }
                    } else {
                        // Default: Use tracked speed
                        CatDueller.bot?.opponentActualSpeed ?: 0.13f
                    }

                    val adjustedVelocity = if (currentSpeed > 0) {
                        // Scale to appropriate speed while maintaining direction
                        val scale = opponentSpeed / currentSpeed
                        Vec3(actualVelocity.xCoord * scale, actualVelocity.yCoord, actualVelocity.zCoord * scale)
                    } else {
                        // If not moving, don't predict movement
                        Vec3(0.0, 0.0, 0.0)  // No movement prediction for stationary targets
                    }

                    val velocity = adjustedVelocity.scale(tickPredict)
                    val flatVelo = Vec3(velocity.xCoord, 0.0, velocity.zCoord)
                    val height = when (dist) {
                        in 0f..8f -> target.eyeHeight * 0.5  // Normal height for close range
                        in 8f..15f -> target.eyeHeight * 1.0  // Normal height for medium range
                        in 15f..25f -> target.eyeHeight * 1.5 
                        in 25f..35f -> target.eyeHeight * 2.0 // Normal height for long range
                        else -> target.eyeHeight * 3.0  // Normal height for very long range
                    }
                    pos = target.positionVector.add(flatVelo).add(Vec3(0.0, height, 0.0)) ?: Vec3(
                        target.posX,
                        target.posY + target.eyeHeight,
                        target.posZ
                    )
                }
            }

            val diffX = pos.xCoord - player.posX
            val diffY: Double = pos.yCoord - (player.posY + player.getEyeHeight().toDouble())
            val diffZ = pos.zCoord - player.posZ
            val dist = MathHelper.sqrt_double(diffX * diffX + diffZ * diffZ).toDouble()
            val yaw = (atan2(diffZ, diffX) * 180.0 / 3.141592653589793).toFloat() - 90.0f
            val pitch = (-(atan2(diffY, dist) * 180.0 / 3.141592653589793)).toFloat()

            if ((crossHairDistance(
                    yaw,
                    pitch,
                    player
                ) > 6 || dist in 2.5..4.0) || (Mouse.isUsingProjectile() && (CatDueller.mc.thePlayer?.heldItem?.unlocalizedName?.lowercase()
                    ?.contains("rod") == true || CatDueller.mc.thePlayer?.heldItem?.unlocalizedName?.lowercase()
                    ?.contains("bow") == true)) || Mouse.isUsingPotion()
            ) {
                if (raw) {
                    floatArrayOf(
                        MathHelper.wrapAngleTo180_float(yaw - player.rotationYaw),
                        MathHelper.wrapAngleTo180_float(pitch - player.rotationPitch)
                    )
                } else floatArrayOf(
                    player.rotationYaw + MathHelper.wrapAngleTo180_float(yaw - player.rotationYaw),
                    player.rotationPitch + MathHelper.wrapAngleTo180_float(pitch - player.rotationPitch)
                )
            } else {
                if (raw) {
                    floatArrayOf(
                        0F, 0F
                    )
                } else {
                    floatArrayOf(
                        player.rotationYaw,
                        player.rotationPitch
                    )
                }
            }
        }
    }

    /**
     * Calculates the angular distance between the player's current crosshair position
     * and target rotation values.
     *
     * @param yaw The target yaw angle in degrees.
     * @param pitch The target pitch angle in degrees.
     * @param player The player entity whose current rotation is compared.
     * @return The Euclidean distance between current and target rotations in degrees.
     */
    fun crossHairDistance(yaw: Float, pitch: Float, player: EntityPlayer): Float {
        val nYaw = abs(player.rotationYaw - yaw)
        val nPitch = abs(player.rotationPitch - pitch)
        return MathHelper.sqrt_float(nYaw * nYaw + nPitch * nPitch)
    }

    /**
     * Calculates the horizontal distance between a player and target entity,
     * ignoring vertical (Y-axis) differences.
     *
     * @param player The player entity.
     * @param target The target entity.
     * @return The horizontal distance in blocks, or 0 if either entity is null.
     */
    fun getDistanceNoY(player: EntityPlayer?, target: Entity?): Float {
        return if (target == null || player == null) {
            0f
        } else {
            val diffX = player.posX - target.posX
            val diffZ = player.posZ - target.posZ
            MathHelper.sqrt_double(diffX * diffX + diffZ * diffZ)
        }
    }

    /**
     * Gets the 2D look vector for an entity on the horizontal plane.
     *
     * Calculates a normalized direction vector based on the entity's yaw rotation,
     * with the Y component set to zero.
     *
     * @param entity The entity whose look vector is calculated.
     * @return A Vec3 representing the horizontal look direction.
     */
    fun get2dLookVec(entity: Entity): Vec3 {
        val yaw = ((entity.rotationYaw + 90) * Math.PI) / 180
        return Vec3(cos(yaw), 0.0, sin(yaw))
    }

    /**
     * Determines if the target entity is moving to the left relative to the viewing entity.
     *
     * @param entity The viewing entity (perspective from which left/right is determined).
     * @param target The entity whose movement direction is being checked.
     * @return True if the target is moving left relative to the viewing entity.
     */
    fun entityMovingLeft(entity: Entity, target: Entity): Boolean {
        val lookVec = get2dLookVec(entity).rotateYaw(90f)
        val entityVec = target.getVelocity()

        val angle =
            acos((lookVec.xCoord * entityVec.xCoord + lookVec.zCoord * entityVec.zCoord) / (lookVec.lengthVector() * entityVec.lengthVector())) * 180 / Math.PI
        return angle > 90
    }

    /**
     * Determines if the target entity is moving to the right relative to the viewing entity.
     *
     * @param entity The viewing entity (perspective from which left/right is determined).
     * @param target The entity whose movement direction is being checked.
     * @return True if the target is moving right relative to the viewing entity.
     */
    fun entityMovingRight(entity: Entity, target: Entity): Boolean {
        val lookVec = get2dLookVec(entity).rotateYaw(90f)
        val entityVec = target.getVelocity()

        val angle =
            acos((lookVec.xCoord * entityVec.xCoord + lookVec.zCoord * entityVec.zCoord) / (lookVec.lengthVector() * entityVec.lengthVector())) * 180 / Math.PI
        return angle < 90
    }

    /**
     * Determines if the target entity is facing away from the viewing entity.
     *
     * Compares the look vectors of both entities to determine if the target
     * is looking in a direction away from the viewing entity.
     *
     * @param entity The viewing entity.
     * @param target The entity whose facing direction is being checked.
     * @return True if the angle between look vectors is between 20 and 70 degrees.
     */
    fun entityFacingAway(entity: Entity, target: Entity): Boolean {
        val vec1 = get2dLookVec(entity)
        val vec2 = get2dLookVec(target)

        val angle =
            acos((vec1.xCoord * vec2.xCoord + vec1.zCoord * vec2.zCoord) / (vec1.lengthVector() * vec2.lengthVector())) * 180 / Math.PI
        return angle in 20f..70f
    }

    /**
     * Finds the two closest corners of a bounding box to the local player.
     *
     * Used for determining the optimal aim point on an entity's hitbox.
     *
     * @param corner1 First corner of the bounding box.
     * @param corner2 Second corner of the bounding box.
     * @param corner3 Third corner of the bounding box.
     * @param corner4 Fourth corner of the bounding box.
     * @return An ArrayList containing the two closest corners, sorted by distance.
     */
    private fun getClosestCorner(corner1: Vec3, corner2: Vec3, corner3: Vec3, corner4: Vec3): ArrayList<Vec3> {
        val pos = Vec3(
            CatDueller.mc.thePlayer.posX,
            CatDueller.mc.thePlayer.posY + CatDueller.mc.thePlayer.eyeHeight,
            CatDueller.mc.thePlayer.posZ
        )

        val smallest = arrayListOf(corner1, corner2, corner3, corner4)
        smallest.sortBy { abs(pos.distanceTo(it)) }

        return arrayListOf(smallest[0], smallest[1])
    }

}
