package best.spaghetcodes.catdueller.bot.bots

import best.spaghetcodes.catdueller.CatDueller
import best.spaghetcodes.catdueller.bot.BotBase
import best.spaghetcodes.catdueller.bot.features.Bow
import best.spaghetcodes.catdueller.bot.features.MovePriority
import best.spaghetcodes.catdueller.bot.features.Rod
import best.spaghetcodes.catdueller.bot.player.Combat
import best.spaghetcodes.catdueller.bot.player.Inventory
import best.spaghetcodes.catdueller.bot.player.Mouse
import best.spaghetcodes.catdueller.bot.player.Movement
import best.spaghetcodes.catdueller.utils.*
import net.minecraft.init.Blocks
import net.minecraft.util.Vec3

class Classic : BotBase("/play duels_classic_duel"), Bow, Rod, MovePriority {

    override fun getName(): String {
        return "Classic"
    }

    init {
        setStatKeys(
            mapOf(
                "wins" to "player.stats.Duels.classic_duel_wins",
                "losses" to "player.stats.Duels.classic_duel_losses",
                "ws" to "player.stats.Duels.current_classic_winstreak",
            )
        )
    }

    var shotsFired = 0
    var maxArrows = 5
    
    // Strafe variables
    private var hurtStrafeDirection = 0  // strafe direction after being hurt: 0=none, 1=left, 2=right
    
    // Track hold left click state to avoid unnecessary calls
    private var shouldHoldLeftClick = false
    
    // Track rod hit to prevent jump interruption
    private var rodHitNeedJump = false
    // Track rod hit distance for debugging
    private var rodHitDistance = 0f  // Store distance when rod hit occurred
    
    // Track rod usage time for accurate hit detection
    private var lastRodUseTime = 0L
    
    // Track opponent's hurtTime for rod hit detection
    private var opponentLastHurtTime = 0
    
    // Track sword hits to exclude them from rod hit detection
    private var lastSwordHitTime = 0L
    
    // Track dodge state to prevent block jump interference
    private var isDodging = false
    
    // Track our own bow usage
    private var ourBowStartTime: Long = 0
    private var isUsingBow = false
    
    // Track weapon switching to add delay before attacking
    private var lastWeaponSwitchTime: Long = 0
    
    // Track retreat state - retreat until rod hits once
    private var shouldRetreatUntilRodHit = false
    private var lastRetreatEndTime = 0L  // Track when retreat last ended for cooldown
    
    // Track opponent arrow firing for all situations
    private var opponentJustFiredArrow = false
    private var lastOpponentArrowFireTime = 0L
    private var bowCounterAttackActive = false  // Track if any bow counter-attack is in progress
    private var lastTickOpponentDrawingBow = false  // Track opponent bow state from previous tick
    private var opponentBowStartTime = 0L  // Track when opponent started drawing bow
    private var blockingEndScheduled = false  // Track if blocking end is scheduled

    private var needJump = false

    override fun onGameStart() {
        super.onGameStart()  // Call parent to check scoreboard
        shotsFired = 0  // Reset arrow count for new game
        
        // Reset strafe variables
        hurtStrafeDirection = 0
        
        // Reset hold left click state
        shouldHoldLeftClick = false
        
        // Reset rod hit jump state
        rodHitNeedJump = false
        // Reset rod hit distance
        rodHitDistance = 0f
        
        // Reset rod usage tracking
        lastRodUseTime = 0L
        
        // Reset opponent hurtTime tracking
        opponentLastHurtTime = 0
        
        // Reset sword hit tracking
        lastSwordHitTime = 0L
        
        // Reset dodge state
        isDodging = false
        
        // Reset bow usage tracking
        ourBowStartTime = 0
        isUsingBow = false
        
        // Reset retreat state
        shouldRetreatUntilRodHit = false
        lastRetreatEndTime = 0L
        
        // Reset bow counter-attack tracking
        opponentJustFiredArrow = false
        lastOpponentArrowFireTime = 0L
        bowCounterAttackActive = false
        opponentBowStartTime = 0L
        blockingEndScheduled = false
        
        // Reset weapon switch tracking
        lastWeaponSwitchTime = 0
        
        Movement.startSprinting()
        Movement.startForward()
        TimeUtils.setTimeout(Movement::startJumping, RandomUtils.randomIntInRange(400, 1200))
    }

    override fun onGameEnd() {
        super.onGameEnd()  // Call parent to handle requeue logic
        
        shotsFired = 0
        shouldHoldLeftClick = false
        
        // Additional cleanup to prevent memory leaks
        cleanupGameResources()
        
        // Stop attacking based on config
        if (CatDueller.config?.holdLeftClick == true) {
            Mouse.stopHoldLeftClick()
        } else {
            Mouse.stopLeftAC()
        }
        
        val i = TimeUtils.setInterval({
            if (CatDueller.config?.holdLeftClick == true) {
                Mouse.stopHoldLeftClick()
            } else {
                Mouse.stopLeftAC()
            }
        }, 100, 100)
        
        TimeUtils.setTimeout(fun () {
            i?.cancel()
        }, RandomUtils.randomIntInRange(200, 400))
        
        // Immediately clear movement like Sumo bot to avoid interfering with celebration
        if (CatDueller.bot?.toggled() == true) {
            Mouse.stopTracking()
            Movement.clearAll()
            Combat.stopRandomStrafe()
        }
    }
    
    /**
     * Clean up game-specific resources to prevent memory leaks
     */
    private fun cleanupGameResources() {
        // Reset all timing variables
        lastRodUseTime = 0L
        lastSwordHitTime = 0L
        ourBowStartTime = 0L
        lastWeaponSwitchTime = 0L
        
        // Reset all state flags
        rodHitNeedJump = false
        isDodging = false
        isUsingBow = false
        shouldRetreatUntilRodHit = false
        tapping = false
        
        // Reset retreat cooldown
        lastRetreatEndTime = 0L
        
        // Reset bow counter-attack tracking
        opponentJustFiredArrow = false
        lastOpponentArrowFireTime = 0L
        bowCounterAttackActive = false
        opponentBowStartTime = 0L
        blockingEndScheduled = false
        
        // Reset distance tracking
        rodHitDistance = 0f
        
        // Reset opponent tracking
        opponentLastHurtTime = 0
        
        // Reset strafe variables
        hurtStrafeDirection = 0
        
        // Reset hold left click state
        shouldHoldLeftClick = false
        
        if (CatDueller.config?.combatLogs == true) {
            ChatUtils.combatInfo("Game resources cleaned up - memory leak prevention")
        }
    }

    var tapping = false

    /**
     * Wrapper function to track rod usage time and trigger delayed jump if close
     */
    private fun useRodWithTracking(isDefensive: Boolean = false) {
        lastRodUseTime = System.currentTimeMillis()
        
        // Check if we should jump after delay when using rod (distance < 5) and rod jump is enabled
        val distance = EntityUtils.getDistanceNoY(mc.thePlayer, opponent())
        val enableRodJump = CatDueller.config?.enableRodJump ?: true
        
        if (distance < 5f && mc.thePlayer.onGround && enableRodJump) {
            rodHitNeedJump = true  // Set flag to prevent jump interruption
            
            // Schedule jump after configured delay
            val jumpDelay = CatDueller.config?.rodJumpDelay ?: 200
            TimeUtils.setTimeout({
                if (mc.thePlayer != null && mc.thePlayer.onGround && rodHitNeedJump) {
                    Movement.singleJump(RandomUtils.randomIntInRange(100, 150))
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtils.combatInfo("Rod delayed jump EXECUTED after ${jumpDelay}ms - distance: $distance")
                    }
                }
                
                // Reset the flag after jump or timeout
                TimeUtils.setTimeout({
                    rodHitNeedJump = false
                }, 500)  // Reset 500ms after jump
            }, jumpDelay)  // Jump after configured delay
            
            if (CatDueller.config?.combatLogs == true) {
                ChatUtils.combatInfo("Rod delayed jump SCHEDULED for ${jumpDelay}ms - distance: $distance")
            }
        } else if (CatDueller.config?.combatLogs == true) {
            val reason = when {
                !enableRodJump -> "rod jump disabled in config"
                distance >= 5f -> "distance: $distance (>= 5) "
                !mc.thePlayer.onGround -> "not on ground"
                else -> "unknown reason"
            }
            ChatUtils.combatInfo("Rod jump SKIPPED - $reason")
        }
        
        if (CatDueller.config?.combatLogs == true) {
            ChatUtils.combatInfo("Rod usage tracked - time: $lastRodUseTime, defensive: $isDefensive")
        }
        
        useRod(isDefensive)
    }

    override fun onAttack() {
        val distance = EntityUtils.getDistanceNoY(mc.thePlayer, opponent())
        
        if (CatDueller.config?.combatLogs == true) {
            ChatUtils.combatInfo("onAttack triggered - distance: $distance")
        }
        
        // Record sword hit time to exclude from rod hit detection
        lastSwordHitTime = System.currentTimeMillis()
        
        if (CatDueller.config?.combatLogs == true) {
            ChatUtils.combatInfo("Sword hit recorded - time: $lastSwordHitTime")
        }
        
        // This is likely a sword hit (rod hits don't trigger onAttack)
        if (mc.thePlayer != null && mc.thePlayer.heldItem != null) {
            val n = mc.thePlayer.heldItem.unlocalizedName.lowercase()
            
            if (n.contains("sword") && distance < 3) {
                Mouse.rClick(RandomUtils.randomIntInRange(80, 100)) // blockhit
            }
        }
        
        if (distance < 3) {
            // Other close range logic can go here if needed
        } else {
            if (!tapping && CatDueller.config?.enableWTap == true) {
                tapping = true
                val delay = CatDueller.config?.wTapDelay ?: 100
                TimeUtils.setTimeout(fun () {
                    val dur = 50  // Normal W-Tap duration
                    Combat.wTap(dur)
                    TimeUtils.setTimeout(fun () {
                        tapping = false
                    }, dur)
                }, delay)
            }
        }
        if (combo >= 2) {
            Movement.clearLeftRight()
        }
    }

    override fun onTick() {
        super.onTick()  // Call BotBase onTick for tracking functions
        var needJump = false
        
        if (mc.thePlayer != null && opponent() != null) {
            // Check for rod hit via opponent's hurtTime change
            val opponentCurrentHurtTime = opponent()!!.hurtTime
            val currentTime = System.currentTimeMillis()
            
            // Detect rod hit: opponent's hurtTime increased AND we used rod recently AND it's NOT a sword hit
            val isRecentSwordHit = currentTime - lastSwordHitTime < 200  // 200ms window for sword hit
            val isRecentRodUse = currentTime - lastRodUseTime < 3000     // Extended to 3 second window for rod use
            
            // Debug rod hit detection conditions
            if (CatDueller.config?.combatLogs == true && shouldRetreatUntilRodHit) {
                ChatUtils.combatInfo("Rod hit check - OpponentHurt: $opponentCurrentHurtTime (was: $opponentLastHurtTime), RecentRod: $isRecentRodUse (${currentTime - lastRodUseTime}ms ago), RecentSword: $isRecentSwordHit")
            }
            
            if (opponentCurrentHurtTime > opponentLastHurtTime && opponentCurrentHurtTime > 0 && 
                isRecentRodUse && !isRecentSwordHit) {
                
                val distance = EntityUtils.getDistanceNoY(mc.thePlayer, opponent())
                
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtils.combatInfo("Rod hit detected via opponent hurtTime - opponent hurtTime: $opponentCurrentHurtTime, distance: $distance, time since rod: ${currentTime - lastRodUseTime}ms, sword hit excluded")
                }
                
                // Stop retreat when rod hits
                if (shouldRetreatUntilRodHit) {
                    shouldRetreatUntilRodHit = false
                    lastRetreatEndTime = System.currentTimeMillis()  // Record retreat end time for cooldown
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtils.combatInfo("Rod hit detected - stopping retreat, cooldown started")
                    }
                }
                
                // W-Tap logic for rod hit - only when distance < 4 blocks and not during rod jump
                if (!tapping && CatDueller.config?.enableWTap == true && distance < 4f && !rodHitNeedJump) {
                    tapping = true
                    val delay = CatDueller.config?.wTapDelay ?: 100
                    TimeUtils.setTimeout(fun () {
                        val dur = 300  // Rod W-Tap duration
                        Combat.wTap(dur)
                        TimeUtils.setTimeout(fun () {
                            tapping = false
                        }, dur)
                    }, delay)
                } else if (CatDueller.config?.combatLogs == true && CatDueller.config?.enableWTap == true) {
                    val reason = when {
                        distance >= 4f -> "distance: $distance (>= 4 blocks)"
                        rodHitNeedJump -> "rod jump active"
                        else -> "unknown reason"
                    }
                    ChatUtils.combatInfo("Rod W-Tap skipped - $reason")
                }
                combo--
            } else if (opponentCurrentHurtTime > opponentLastHurtTime && opponentCurrentHurtTime > 0) {
                // Debug why rod hit wasn't detected
                if (CatDueller.config?.combatLogs == true && shouldRetreatUntilRodHit) {
                    val reason = when {
                        !isRecentRodUse -> "no recent rod use (${currentTime - lastRodUseTime}ms ago)"
                        isRecentSwordHit -> "recent sword hit (${currentTime - lastSwordHitTime}ms ago)"
                        else -> "unknown reason"
                    }
                    ChatUtils.combatInfo("Opponent hurt but rod hit not detected - $reason")
                }
            }
            
            opponentLastHurtTime = opponentCurrentHurtTime
            
            // Track opponent arrow firing for situation5
            // Check if opponent stopped drawing bow (likely fired arrow)
            // Use previous tick's state to detect the transition
            
            // Track opponent bow drawing time for 700ms blocking
            if (!lastTickOpponentDrawingBow && opponentIsDrawingBow) {
                // Opponent just started drawing bow
                opponentBowStartTime = currentTime
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtils.combatInfo("Opponent started drawing bow - tracking time for 700ms block")
                }
            } else if (lastTickOpponentDrawingBow && !opponentIsDrawingBow) {
                // Opponent stopped drawing bow (fired arrow)
                opponentJustFiredArrow = true
                lastOpponentArrowFireTime = currentTime
                opponentBowStartTime = 0L  // Reset bow start time
                
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtils.combatInfo("Opponent fired arrow - arrow fired flag set")
                }
            }
            
            // Start arrow blocking after 700ms of opponent drawing bow
            // But only if distance is greater than 6 blocks (close range doesn't need blocking)
            val distance = EntityUtils.getDistanceNoY(mc.thePlayer, opponent())
            if (opponentIsDrawingBow && opponentBowStartTime > 0 && 
                currentTime - opponentBowStartTime >= 500 && !Mouse.isBlockingArrow() && distance > 6f) {
                
                // Start arrow blocking - this will prevent other actions
                Mouse.setBlockingArrow(true)
                
                // Arrow block: interrupt any ongoing rod usage and switch to sword
                if (Mouse.isUsingProjectile() || this.rodRetractTimeout != null) {
                    // Interrupt rod usage for arrow blocking
                    immediateRetractRod()
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtils.combatInfo("Interrupted rod usage for arrow blocking")
                    }
                }
                
                // Calculate block duration based on distance: distance(blocks) x 20ms
                val distance = EntityUtils.getDistanceNoY(mc.thePlayer, opponent())
                val blockDuration = (distance * 20).toInt().coerceIn(200, 2000)  // Min 200ms, Max 2000ms
                
                // Check if we're currently using bow or rod (right-click active)
                val wasUsingProjectile = Mouse.isUsingProjectile() || Mouse.rClickDown
                
                // Ensure we have a sword for blocking - switch without releasing right click if we were using projectile
                if (mc.thePlayer.heldItem == null || !mc.thePlayer.heldItem.unlocalizedName.lowercase().contains("sword")) {
                    Inventory.setInvItem("sword")
                    
                    if (wasUsingProjectile) {
                        // If we were using projectile, continue holding right click for seamless blocking
                        if (!Mouse.rClickDown) {
                            Mouse.startRightClick()  // Start holding right click indefinitely
                        }
                        // If already holding right click, just continue holding
                        
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtils.combatInfo("Seamless transition from projectile to sword blocking (no right-click release)")
                        }
                    } else {
                        // Not using projectile, start fresh block
                        Mouse.rClickUp()  // Release any ongoing right click
                        Mouse.startRightClick()  // Start holding right click indefinitely
                    }
                } else {
                    // Already have sword, start blocking
                    if (!wasUsingProjectile) {
                        Mouse.rClickUp()  // Release any ongoing right click only if not using projectile
                    }
                    if (!Mouse.rClickDown) {
                        Mouse.startRightClick()  // Start holding right click indefinitely
                    }
                }
                
                // Note: Block duration will be managed by checking opponent bow state each tick
                // No setTimeout for ending block - it will end when opponent stops drawing bow
                
                if (CatDueller.config?.combatLogs == true) {
                    val transitionType = if (wasUsingProjectile) "seamless" else "fresh"
                    ChatUtils.combatInfo("Started blocking arrow after 700ms draw time (${transitionType} transition, distance: ${String.format("%.1f", distance)} blocks) - will block until opponent stops drawing")
                }
            } else if (opponentIsDrawingBow && opponentBowStartTime > 0 && 
                       currentTime - opponentBowStartTime >= 500 && !Mouse.isBlockingArrow() && distance <= 5f) {
                // Debug: explain why blocking is not started at close range
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtils.combatInfo("Arrow blocking skipped - distance too close (${String.format("%.1f", distance)} blocks ≤ 6)")
                }
            }
            
            // Check if opponent has been drawing bow for more than 1.2 seconds - stop blocking if so
            // But don't interfere if we're using rod or bow
            if (Mouse.isBlockingArrow() && opponentIsDrawingBow && opponentBowStartTime > 0 && 
                currentTime - opponentBowStartTime > 1200 && !blockingEndScheduled &&
                !Mouse.isUsingProjectile()) {  // Don't stop blocking if we're using rod/bow
                
                blockingEndScheduled = true  // Prevent multiple scheduling
                
                // Stop blocking immediately when opponent draws bow too long
                Mouse.setBlockingArrow(false)
                Mouse.rClickUp()  // Release right click immediately
                blockingEndScheduled = false  // Reset flag
                
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtils.combatInfo("Arrow blocking ended - opponent drawing bow too long (${currentTime - opponentBowStartTime}ms > 1200ms)")
                }
            } else if (Mouse.isBlockingArrow() && opponentIsDrawingBow && opponentBowStartTime > 0 && 
                       currentTime - opponentBowStartTime > 1200 && Mouse.isUsingProjectile()) {
                // Debug: explain why timeout is skipped due to rod/bow usage
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtils.combatInfo("Arrow blocking timeout skipped - currently using rod/bow")
                }
            }
            
            // Check if distance became too close (≤6 blocks) - stop blocking if so
            if (Mouse.isBlockingArrow() && distance <= 6f && !blockingEndScheduled) {
                blockingEndScheduled = true  // Prevent multiple scheduling
                
                // Stop blocking immediately when distance is too close
                Mouse.setBlockingArrow(false)
                Mouse.rClickUp()  // Release right click immediately
                blockingEndScheduled = false  // Reset flag
                
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtils.combatInfo("Arrow blocking ended - distance too close (${String.format("%.1f", distance)} blocks ≤ 6)")
                }
            }
            
            // Check if we should stop blocking when opponent stops drawing bow
            // Use distance-based delay to account for arrow flight time
            if (Mouse.isBlockingArrow() && !opponentIsDrawingBow && !blockingEndScheduled) {
                // Calculate delay based on distance: longer distance = longer arrow flight time
                val distance = EntityUtils.getDistanceNoY(mc.thePlayer, opponent())
                val flightTimeDelay = when {
                    distance <= 5f -> 100   // Very close: 100ms delay
                    distance <= 10f -> 200  // Close: 200ms delay
                    distance <= 15f -> 300  // Medium: 300ms delay
                    distance <= 20f -> 400  // Far: 400ms delay
                    else -> 500             // Very far: 500ms delay
                }
                
                blockingEndScheduled = true  // Prevent multiple scheduling
                
                TimeUtils.setTimeout({
                    if (Mouse.isBlockingArrow()) {  // Double check we're still blocking
                        Mouse.setBlockingArrow(false)
                        Mouse.rClickUp()  // Release right click after delay
                        blockingEndScheduled = false  // Reset flag
                        
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtils.combatInfo("Arrow blocking ended after ${flightTimeDelay}ms delay (distance: ${String.format("%.1f", distance)} blocks)")
                        }
                    }
                }, flightTimeDelay)
                
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtils.combatInfo("Opponent stopped drawing bow - scheduled blocking end in ${flightTimeDelay}ms (distance: ${String.format("%.1f", distance)} blocks)")
                }
            }
            
            // Update previous tick state for next comparison
            lastTickOpponentDrawingBow = opponentIsDrawingBow
            
            // Reset arrow fired flag after 3 seconds
            if (opponentJustFiredArrow && currentTime - lastOpponentArrowFireTime > 3000) {
                opponentJustFiredArrow = false
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtils.combatInfo("Situation5: Arrow fired flag reset after 3 seconds")
                }
            }
        }
        
        if (mc.thePlayer != null) {
            // Then check for block in front (always check, even when dodging)
            if (WorldUtils.blockInFront(mc.thePlayer, 2f, 0.5f) != Blocks.air && mc.thePlayer.onGround && !needJump) {
                needJump = true
                Movement.singleJump(RandomUtils.randomIntInRange(150, 250))
            }
        }
        
        if (opponent() != null && mc.theWorld != null && mc.thePlayer != null) {
            if (!mc.thePlayer.isSprinting) {
                Movement.startSprinting()
            }

            val distance = EntityUtils.getDistanceNoY(mc.thePlayer, opponent())
          
            // Check if rod should be immediately retracted due to close distance
            // Only retract non-defensive rods due to distance
            if (distance < 3.0f && this.rodRetractTimeout != null && !this.isDefensiveRod) {
                immediateRetractRod()
            }

            if (distance < (CatDueller.config?.maxDistanceLook ?: 150)) {
                Mouse.startTracking()
            } else {
                Mouse.stopTracking()
            }

            if (CatDueller.config?.holdLeftClick == true) {
                // Hold Left Click mode: Distance-based control only (no crosshair check)
                val maxAttackDistance = CatDueller.config?.maxDistanceAttack ?: 5
                val hasSword = mc.thePlayer.heldItem != null && mc.thePlayer.heldItem.unlocalizedName.lowercase().contains("sword")
                val inRange = distance <= maxAttackDistance
                
                // Add delay after weapon switching to avoid immediate attack
                val timeSinceWeaponSwitch = System.currentTimeMillis() - lastWeaponSwitchTime
                val weaponSwitchDelay = 200  // 200ms delay after weapon switch
                val canAttackAfterSwitch = timeSinceWeaponSwitch > weaponSwitchDelay
                
                val newShouldHoldLeftClick = inRange && hasSword && canAttackAfterSwitch
                
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtils.combatInfo("HoldLeftClick - Distance: $distance/$maxAttackDistance, HasSword: $hasSword, InRange: $inRange, CanAttack: $canAttackAfterSwitch (${timeSinceWeaponSwitch}ms), Should: $newShouldHoldLeftClick, Current: $shouldHoldLeftClick")
                }
                
                if (newShouldHoldLeftClick != shouldHoldLeftClick) {
                    shouldHoldLeftClick = newShouldHoldLeftClick
                    if (shouldHoldLeftClick) {
                        Mouse.startHoldLeftClick()
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtils.combatInfo("Started hold left click")
                        }
                    } else {
                        Mouse.stopHoldLeftClick()
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtils.combatInfo("Stopped hold left click")
                        }
                    }
                }
            } else {
                // Normal mode: Use shouldStartAttacking with crosshair checks
                if (shouldStartAttacking(distance)) {
                    if (mc.thePlayer.heldItem != null && mc.thePlayer.heldItem.unlocalizedName.lowercase().contains("sword")) {
                        Mouse.startLeftAC()  // Start continuous attacking, hit select will handle cancellation
                    }
                } else {
                    Mouse.stopLeftAC()
                }
            }

            if (distance > 8.8) {
                // Use opponentIsDrawingBow for more accurate dodge detection
                if (opponentIsDrawingBow) {
                    isDodging = true
                    if (!EntityUtils.entityFacingAway(mc.thePlayer, opponent()!!) && !needJump && !rodHitNeedJump) {
                        Movement.stopJumping()
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtils.combatInfo("Dodge: Opponent drawing bow - stopping jump")
                        }
                    } else {
                        Movement.startJumping()
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtils.combatInfo("Dodge: Opponent drawing bow - continuing jump (facing away or needJump or rodHitNeedJump)")
                        }
                    }
                } else {
                    Movement.startJumping()
                    isDodging = false
                }
            } else {
                if (needJump || rodHitNeedJump) {
                    Movement.startJumping()
                } else {
                    Movement.stopJumping()
                }
                isDodging = false
            }

            val movePriority = arrayListOf(0, 0)
            var clear = false
            var randomStrafe = false

            // Retreat logic: low health + medium distance - retreat until rod hits once
            // Start retreat when: low health + health disadvantage + medium distance + cooldown expired + enabled in config
            // Allow re-triggering retreat after rod hit if player moves back into retreat range
            val currentTime = System.currentTimeMillis()
            val retreatCooldownExpired = currentTime - lastRetreatEndTime > 3000  // 3 second cooldown
            val enableRetreat = CatDueller.config?.enableRetreat ?: true
            
            val shouldStartRetreat = enableRetreat &&
                                   mc.thePlayer.health < 16f && 
                                   mc.thePlayer.health < opponent()!!.health && 
                                   distance in 7f..10f &&
                                   !shouldRetreatUntilRodHit &&  // Only start if not already retreating
                                   retreatCooldownExpired  // Must wait for cooldown
            
            // Continue retreat until rod hits (or conditions no longer met)
            // IMPORTANT: Only continue retreat if still in 7-10 block range (same as start condition)
            val shouldContinueRetreat = enableRetreat &&
                                      shouldRetreatUntilRodHit && 
                                      mc.thePlayer.health < 16f && 
                                      mc.thePlayer.health < opponent()!!.health &&
                                      distance in 7f..10f  // Must stay in retreat range
            
            // Defensive rod retreat: retreat when using defensive rod at close distance
            val shouldDefensiveRodRetreat = this.isDefensiveRod && Mouse.isUsingProjectile() && distance < 6f
            
            // Start retreat if conditions are met
            if (shouldStartRetreat) {
                shouldRetreatUntilRodHit = true
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtils.combatInfo("Starting retreat until rod hit - Low health (${mc.thePlayer.health}) vs opponent (${opponent()!!.health}), distance: $distance, cooldown: ${currentTime - lastRetreatEndTime}ms")
                }
            } else if (mc.thePlayer.health < 16f && mc.thePlayer.health < opponent()!!.health && distance in 7f..10f && !shouldRetreatUntilRodHit) {
                // Debug: retreat blocked by cooldown or disabled in config
                if (CatDueller.config?.combatLogs == true) {
                    if (!enableRetreat) {
                        ChatUtils.combatInfo("Retreat blocked - disabled in config")
                    } else if (!retreatCooldownExpired) {
                        val remainingCooldown = 3000 - (currentTime - lastRetreatEndTime)
                        ChatUtils.combatInfo("Retreat blocked by cooldown - ${remainingCooldown}ms remaining")
                    }
                }
            }
            
            if (shouldContinueRetreat || shouldDefensiveRodRetreat) {
                Movement.stopForward()
                Movement.startBackward()
                
                if (CatDueller.config?.combatLogs == true) {
                    val retreatReason = if (shouldContinueRetreat) {
                        "Continuing retreat until rod hit - Low health (${mc.thePlayer.health}) vs opponent (${opponent()!!.health}), distance: $distance"
                    } else {
                        "Defensive rod retreat - Using defensive rod at close distance: $distance"
                    }
                    ChatUtils.combatInfo(retreatReason)
                }
            } else {
                Movement.stopBackward()  // Stop retreating when not needed
                
                // Reset retreat state if conditions no longer met OR if distance is outside retreat range
                // BACKUP: Also cancel retreat if we recently used rod and opponent was hurt (backup rod hit detection)
                val currentTime = System.currentTimeMillis()
                val backupRodHitDetection = shouldRetreatUntilRodHit && 
                                          (currentTime - lastRodUseTime < 4000) && 
                                          (opponent()!!.hurtTime > 0) &&
                                          (currentTime - lastSwordHitTime > 500)  // Make sure it's not a sword hit
                
                if (shouldRetreatUntilRodHit && (!enableRetreat || mc.thePlayer.health >= 16f || mc.thePlayer.health >= opponent()!!.health || distance < 7f || distance > 10f || backupRodHitDetection)) {
                    shouldRetreatUntilRodHit = false
                    lastRetreatEndTime = currentTime  // Record retreat end time for cooldown
                    if (CatDueller.config?.combatLogs == true) {
                        val reason = when {
                            !enableRetreat -> "retreat disabled in config"
                            mc.thePlayer.health >= 16f -> "health recovered"
                            mc.thePlayer.health >= opponent()!!.health -> "health advantage gained"
                            distance < 7f -> "moved too close (< 7 blocks)"
                            distance > 10f -> "moved too far (> 10 blocks)"
                            backupRodHitDetection -> "backup rod hit detection (opponent hurt: ${opponent()!!.hurtTime}, rod ${currentTime - lastRodUseTime}ms ago)"
                            else -> "conditions no longer met"
                        }
                        ChatUtils.combatInfo("Retreat cancelled - $reason, cooldown started")
                    }
                }
                
                if (distance < 1 || (distance < 2.7 && combo >= 1)) {
                    Movement.stopForward()
                } else {
                    if (!tapping) {
                        Movement.startForward()
                    }
                }
            }

            if (distance < 1.5 && mc.thePlayer.heldItem != null && !mc.thePlayer.heldItem.unlocalizedName.lowercase().contains("sword")) {
                Inventory.setInvItem("sword")
                Mouse.rClickUp()
                // Don't start attacking here - let the distance control logic handle it
            }

            // Calculate adjusted rod distances based on prediction ticks bonus
            val predictionTicksBonus = CatDueller.config?.predictionTicksBonus ?: 0
            val opponentActualSpeed = CatDueller.bot?.opponentActualSpeed ?: 0.13f  // Use opponent's actual speed
            
            // Apply counter-strafe multiplier when counter-strafing (both moving away laterally)
            val counterStrafeMultiplier = if (isCounterStrafing) (CatDueller.config?.counterStrafeBonus ?: 1.5f) else 1.0f
            val basePredictionDistance = predictionTicksBonus * opponentActualSpeed
            val distanceAdjustment = basePredictionDistance * counterStrafeMultiplier
            
            if (CatDueller.config?.combatLogs == true && isCounterStrafing) {
                ChatUtils.combatInfo("Counter-strafe detected - applying ${counterStrafeMultiplier}x prediction multiplier")
            }
            
            // Adjust rod usage distances based on prediction compensation
            // Extend minimum range when opponent is retreating
            val baseRodDistance1Min = if (opponentIsRetreating) 3.5f else 4.0f
            val rodDistance1Min = baseRodDistance1Min + distanceAdjustment
            val rodDistance1Max = 7.2f + distanceAdjustment
            val rodDistance2Min = 8.5f + distanceAdjustment
            val rodDistance2Max = 10.0f + distanceAdjustment
        
            
            // Check for defensive rod usage (opponent combo >= 3)
            val shouldUseDefensiveRod = opponentCombo >= 3 && distance > 3
            // Check for offensive rod usage (distance-based)
            val shouldUseOffensiveRod = (distance in rodDistance1Min..rodDistance1Max || distance in rodDistance2Min..rodDistance2Max) && 
                                       opponent() != null && !EntityUtils.entityFacingAway(mc.thePlayer, opponent()!!)
            
            // Check if we should avoid rod usage due to close range + opponent drawing bow
            val shouldAvoidRodDueToCloseRangeBow = distance <= 5f && opponentIsDrawingBow
            
            if (shouldAvoidRodDueToCloseRangeBow) {
                // Start jumping to dodge arrows at close range instead of using rod
                needJump = true
                Movement.startJumping()
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtils.combatInfo("Avoiding rod usage and jumping - close range (${String.format("%.1f", distance)} blocks ≤ 6) + opponent drawing bow")
                }
            }

            
            // Debug rod range extension
            if (CatDueller.config?.combatLogs == true && opponentIsRetreating && shouldUseOffensiveRod) {
                ChatUtils.combatInfo("Extended rod range activated - opponent retreating (min: ${String.format("%.1f", rodDistance1Min)})")
            }
            
            
            if ((shouldUseDefensiveRod || shouldUseOffensiveRod) && !Mouse.isUsingProjectile() && !Mouse.isBlockingArrow() && !shouldAvoidRodDueToCloseRangeBow) {
                if (CatDueller.config?.combatLogs == true) {
                    val rodType = if (shouldUseDefensiveRod) "defensive" else "offensive"
                    val rangeInfo = if (distance in rodDistance2Min..rodDistance2Max) "Range2" else "Range1"
                    ChatUtils.combatInfo("Using rod ($rodType, $rangeInfo) at distance ${String.format("%.2f", distance)}")
                }
                useRodWithTracking(shouldUseDefensiveRod)  // Pass true if defensive, false if offensive
            } else if ((shouldUseDefensiveRod || shouldUseOffensiveRod) && shouldAvoidRodDueToCloseRangeBow) {
                // Debug: explain why rod usage is skipped due to close range + opponent drawing bow
                if (CatDueller.config?.combatLogs == true) {
                    val rodType = if (shouldUseDefensiveRod) "defensive" else "offensive"
                    ChatUtils.combatInfo("Rod usage skipped ($rodType) - close range (${String.format("%.1f", distance)} blocks ≤ 6) + opponent drawing bow")
                }
            }

            // Smart weapon switching based on distance and situation
            // Combine offensive rod ranges with defensive rod condition
            val inRodRange = (distance in rodDistance1Min..rodDistance1Max || distance in rodDistance2Min..rodDistance2Max) || shouldUseDefensiveRod
            val shouldHaveSword = distance < 4f || (!inRodRange && distance < 6f)
            
            // Check if rod is currently in use (don't interrupt rod usage)
            // Also check if we're using projectile even if not holding rod (to prevent weapon switching during rod usage)
            val isRodInUse = Mouse.isUsingProjectile() && (
                (mc.thePlayer.heldItem != null && mc.thePlayer.heldItem.unlocalizedName.lowercase().contains("rod")) ||
                this.rodRetractTimeout != null  // Rod is still active even if we're not holding it
            )
            
            if (mc.thePlayer.heldItem != null && !isRodInUse) {
                val currentItem = mc.thePlayer.heldItem.unlocalizedName.lowercase()
                
                // Switch to sword if we should have sword but don't
                if (shouldHaveSword && !currentItem.contains("sword")) {
                    Inventory.setInvItem("sword")
                    Mouse.rClickUp()
                    // Don't start attacking here - let the distance control logic handle it
                }
                // Switch to rod if we're in rod range but holding bow
                else if (inRodRange && currentItem.contains("bow") && !Mouse.isUsingProjectile()) {
                    Inventory.setInvItem("sword")  // Switch to sword first, rod usage will switch to rod when needed
                    Mouse.rClickUp()
                }
            }
            
            // Debug rod usage protection
            if (CatDueller.config?.combatLogs == true && isRodInUse && shouldHaveSword) {
                ChatUtils.combatInfo("Rod in use - weapon switching blocked until rod completes")
            }

            // Check if opponent is actually drawing bow (not just holding it) to allow our bow usage
            if (opponent() != null && opponentIsDrawingBow) {
                opponentUsedBow = true
                
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtils.combatInfo("Opponent used bow - drawing detected")
                }
            }

            // Situation 1: Enemy facing away (6-30 blocks) - wait for opponent to fire arrow
            val situation1 = (EntityUtils.entityFacingAway(mc.thePlayer, opponent()!!) || (opponentIsRetreating)) && distance in 6f..30f &&
                            !opponentIsDrawingBow
            // Situation 2: Long distance (28-33 blocks) - wait for opponent to fire arrow
            val situation2 = distance in 28.0..33.0 && !EntityUtils.entityFacingAway(mc.thePlayer, opponent()!!) &&
                            !opponentIsDrawingBow
            // Situation 3: Low health opponent (< 2 hearts, distance > 8) - wait for opponent to fire arrow
            val situation3 = opponent()!!.health < 4.0f && (distance > 10.0f || (distance > 6.0f && opponentIsRetreating)) &&
                            !opponentIsDrawingBow
            // Situation 4: Our health lower than opponent's health and distance > 10 - wait for opponent to fire arrow
            val situation4 = mc.thePlayer.health < opponent()!!.health && distance > 10.0f &&
                            !opponentIsDrawingBow
            // Situation 5: Counter-attack AFTER opponent fires arrow - wait for arrow then counter-attack
            val situation5 = opponentJustFiredArrow && distance in 8f..20f
            
            // First, check if we should interrupt our bow usage when opponent starts drawing
            // All situations now wait for opponent to fire before starting, so only interrupt non-protected situations
            if (Mouse.isUsingProjectile() && isUsingBow && opponentIsDrawingBow) {
                val bowUsageTime = System.currentTimeMillis() - ourBowStartTime
                val isProtectedSituation = situation3 || situation4 || situation5
                
                if (bowUsageTime < 2000 && !isProtectedSituation && !bowCounterAttackActive) {
                    // Interrupt bow usage to focus on dodging - switch to sword and release right click
                    Mouse.setUsingProjectile(false)
                    Inventory.setInvItem("sword")
                    Mouse.rClickUp()
                    isUsingBow = false
                    lastWeaponSwitchTime = System.currentTimeMillis()  // Record weapon switch time
                    
                    if (CatDueller.config?.combatLogs == true) {
                        ChatUtils.combatInfo("Interrupted bow usage to dodge - used for ${bowUsageTime}ms")
                    }
                } else if ((isProtectedSituation || bowCounterAttackActive) && CatDueller.config?.combatLogs == true) {
                    val situationType = when {
                        situation3 -> "situation 3 (low health opponent)"
                        situation4 -> "situation 4 (our health disadvantage)"
                        situation5 -> "situation 5 (bow counter-attack)"
                        bowCounterAttackActive -> "bow counter-attack in progress"
                        else -> "protected situation"
                    }
                    ChatUtils.combatInfo("Bow interruption skipped - $situationType")
                }
            }
            
            // Check if we should interrupt bow usage due to close distance (≤6 blocks)
            if (isUsingBow && Mouse.isUsingProjectile() && distance <= 7f) {
                // Interrupt bow usage immediately when opponent gets too close
                Mouse.setUsingProjectile(false)
                Inventory.setInvItem("sword")
                Mouse.rClickUp()
                isUsingBow = false
                bowCounterAttackActive = false  // Reset bow counter-attack
                
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtils.combatInfo("Bow usage interrupted - opponent too close (${String.format("%.1f", distance)} blocks ≤ 6)")
                }
            }
            
            if ((situation1 || situation2 || situation3 || situation4 || situation5) && !Mouse.isBlockingArrow()) {
                val canUseBow = if (situation1) {
                    // Situation 1: Start bow usage if not already active
                    val canStart = distance > 6 && !Mouse.isUsingProjectile() && shotsFired < maxArrows
                    
                    if (canStart && !bowCounterAttackActive) {
                        bowCounterAttackActive = true
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtils.combatInfo("Situation1: Starting bow usage after opponent fired/not drawing")
                        }
                    }
                    
                    canStart || (bowCounterAttackActive && Mouse.isUsingProjectile())
                } else if (situation2) {
                    // Situation 2: Start bow usage if not already active (requires opponentUsedBow)
                    val canStart = distance > 6 && !Mouse.isUsingProjectile() && shotsFired < maxArrows && opponentUsedBow
                    
                    if (canStart && !bowCounterAttackActive) {
                        bowCounterAttackActive = true
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtils.combatInfo("Situation2: Starting bow usage after opponent fired/not drawing")
                        }
                    }
                    
                    canStart || (bowCounterAttackActive && Mouse.isUsingProjectile())
                } else if (situation3) {
                    // Situation 3: Start bow usage if not already active
                    val canStart = distance > 6 && !Mouse.isUsingProjectile() && shotsFired < maxArrows
                    
                    if (canStart && !bowCounterAttackActive) {
                        bowCounterAttackActive = true
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtils.combatInfo("Situation3: Starting bow usage after opponent fired/not drawing")
                        }
                    }
                    
                    canStart || (bowCounterAttackActive && Mouse.isUsingProjectile())
                } else if (situation4) {
                    // Situation 4: Start bow usage if not already active
                    val canStart = distance > 6 && !Mouse.isUsingProjectile() && shotsFired < maxArrows
                    
                    if (canStart && !bowCounterAttackActive) {
                        bowCounterAttackActive = true
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtils.combatInfo("Situation4: Starting bow usage after opponent fired/not drawing")
                        }
                    }
                    
                    canStart || (bowCounterAttackActive && Mouse.isUsingProjectile())
                } else if (situation5) {
                    // Situation 5: Counter-attack AFTER opponent fires arrow
                    val canStart = distance > 6 && !Mouse.isUsingProjectile() && shotsFired < maxArrows
                    
                    if (canStart && !bowCounterAttackActive) {
                        bowCounterAttackActive = true
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtils.combatInfo("Situation5: Starting counter-attack after opponent fired arrow")
                        }
                    }
                    
                    canStart || (bowCounterAttackActive && Mouse.isUsingProjectile())
                } else {
                    false
                }
                
                if (CatDueller.config?.combatLogs == true) {
                    if (opponentIsDrawingBow && !bowCounterAttackActive) {
                        ChatUtils.combatInfo("Bow usage waiting - opponent is drawing bow")
                    }
                }
                
                if (canUseBow) {
                    clear = true
                    // Track bow usage start time
                    if (!isUsingBow) {
                        ourBowStartTime = System.currentTimeMillis()
                        isUsingBow = true
                    }
                    
                    useBow(distance, fun () {
                        shotsFired++
                        isUsingBow = false  // Reset when bow usage completes
                        
                        // Reset bow counter-attack when bow usage completes
                        if (bowCounterAttackActive) {
                            bowCounterAttackActive = false
                            if (CatDueller.config?.combatLogs == true) {
                                ChatUtils.combatInfo("Bow counter-attack completed - reset flag")
                            }
                        }
                    })
                } else {
                    clear = false
                    if (WorldUtils.leftOrRightToPoint(mc.thePlayer, Vec3(0.0, 0.0, 0.0))) {
                        movePriority[0] += 4
                    } else {
                        movePriority[1] += 4
                    }
                }
            } else {
                if (EntityUtils.entityFacingAway(mc.thePlayer, opponent()!!)) {
                    if (WorldUtils.leftOrRightToPoint(mc.thePlayer, Vec3(0.0, 0.0, 0.0))) {
                        movePriority[0] += 4
                    } else {
                        movePriority[1] += 4
                    }
                } else {
                    // Distance <= 8.8: Always use random strafe regardless of opponent's weapon or state
                    if (distance <= 8.8f) {
                        randomStrafe = true
                        if (distance < 15 && !needJump && !rodHitNeedJump) {
                            Movement.stopJumping()
                        }
                        
                        if (CatDueller.config?.combatLogs == true) {
                            ChatUtils.combatInfo("Random strafe activated - distance: ${String.format("%.1f", distance)} blocks (≤ 8.8)")
                        }
                    } else if (distance in 15f..8.8f) {
                        randomStrafe = true
                    } else {
                        randomStrafe = false
                        // Dodge strafe when opponent is drawing bow, has rod, or when we're in dodge mode
                        if (opponent() != null) {
                            val hasRod = opponent()!!.heldItem != null && opponent()!!.heldItem.unlocalizedName.lowercase().contains("rod")
                            
                            // Use opponentIsDrawingBow for more accurate detection
                            if (hasRod || opponentIsDrawingBow || isDodging) {
                                randomStrafe = true
                                if (distance < 15 && !needJump && !rodHitNeedJump) {
                                    Movement.stopJumping()
                                }
                                
                                if (CatDueller.config?.combatLogs == true && (opponentIsDrawingBow || isDodging)) {
                                    ChatUtils.combatInfo("Dodge strafe activated - opponent drawing bow: $opponentIsDrawingBow, dodging: $isDodging")
                                }
                            }
                        }
                        
                        if (!randomStrafe) {
                            if (distance < 8) {
                                val rotations = EntityUtils.getRotations(opponent()!!, mc.thePlayer, false)
                                if (rotations != null) {
                                    if (rotations[0] < 0) {
                                        movePriority[1] += 5
                                    } else {
                                        movePriority[0] += 5
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Wall avoidance: simple wall detection using blockInFront logic (used by all strafe logic)
            fun hasWallInDirection(yaw: Float, distance: Float): Boolean {
                val lookVec = EntityUtils.get2dLookVec(mc.thePlayer).rotateYaw(yaw)
                val checkPos = mc.thePlayer.position.add(lookVec.xCoord * distance, 0.0, lookVec.zCoord * distance)
                val block = mc.theWorld.getBlockState(checkPos).block
                return block != Blocks.air
            }
            
            val hasWallOnLeft = hasWallInDirection(90f, 1f) || hasWallInDirection(90f, 2f) || hasWallInDirection(90f, 3f)
            val hasWallOnRight = hasWallInDirection(-90f, 1f) || hasWallInDirection(-90f, 2f) || hasWallInDirection(-90f, 3f)

            // Hurt strafe logic - only when hurt
            val player = mc.thePlayer
            val currentHurtTime = player?.hurtTime ?: 0
            
            // Check for hurt strafe activation (at hurtTime = 4, which is 400ms after hit)
            if (currentHurtTime == 4 && CatDueller.config?.hurtStrafe == true) {
                // Always activate hurt strafe - decide direction randomly
                hurtStrafeDirection = decideRandomStrafeDirection()
                
                // Auto stop after 400ms
                TimeUtils.setTimeout({
                    hurtStrafeDirection = 0
                }, 400)
            }
            
            // Check if hurt strafe is active
            val hasActiveHurtStrafe = hurtStrafeDirection != 0 && 
                                     opponent() != null && 
                                     mc.thePlayer != null
            
            // HURT STRAFE HAS HIGHEST PRIORITY - but consider walls
            if (hasActiveHurtStrafe) {
                // Force execute hurt strafe but avoid walls
                Combat.stopRandomStrafe()
                Movement.clearLeftRight()
                
                // Check if hurt strafe direction would hit a wall
                val wouldHitWall = when (hurtStrafeDirection) {
                    1 -> hasWallOnLeft  // Moving left, check left wall
                    2 -> hasWallOnRight // Moving right, check right wall
                    else -> false
                }
                
                if (wouldHitWall) {
                    // Reverse hurt strafe direction to avoid wall
                    when (hurtStrafeDirection) {
                        1 -> {
                            Movement.stopLeft()
                            Movement.startRight()
                            if (CatDueller.config?.combatLogs == true) {
                                ChatUtils.combatInfo("Hurt strafe reversed - avoiding left wall")
                            }
                        }
                        2 -> {
                            Movement.stopRight()
                            Movement.startLeft()
                            if (CatDueller.config?.combatLogs == true) {
                                ChatUtils.combatInfo("Hurt strafe reversed - avoiding right wall")
                            }
                        }
                    }
                } else {
                    // Normal hurt strafe execution
                    when (hurtStrafeDirection) {
                        1 -> {
                            Movement.stopRight()
                            Movement.startLeft()
                        }
                        2 -> {
                            Movement.stopLeft()
                            Movement.startRight()
                        }
                    }
                }
            }

            // Wall avoidance priority adjustment (applies to all strafe logic)
            if (hasWallOnLeft && !hasWallOnRight) {
                // Wall on left, prefer right movement
                movePriority[1] += 20  // Higher priority than strafe
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtils.combatInfo("Wall on left - moving right")
                }
            } else if (hasWallOnRight && !hasWallOnLeft) {
                // Wall on right, prefer left movement
                movePriority[0] += 20  // Higher priority than strafe
                if (CatDueller.config?.combatLogs == true) {
                    ChatUtils.combatInfo("Wall on right - moving left")
                }
            }

            // Check if hurt strafe is active - if so, skip handle() to avoid being overridden
            if (!hasActiveHurtStrafe) {
                handle(clear, randomStrafe, movePriority)
            }
            // If hurt strafe is active, movement is already handled above, skip handle()
        }
    }

    /**
     * Decide strafe direction randomly
     * Returns 1 for left, 2 for right
     */
    private fun decideRandomStrafeDirection(): Int {
        return if (RandomUtils.randomBool()) 1 else 2
    }

}
