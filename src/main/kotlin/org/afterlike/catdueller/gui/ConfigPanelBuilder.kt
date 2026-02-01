package org.afterlike.catdueller.gui

import org.afterlike.catdueller.CatDueller
import org.afterlike.catdueller.gui.components.Panel
import org.afterlike.catdueller.gui.components.settings.BooleanSetting
import org.afterlike.catdueller.gui.components.settings.SelectorSetting
import org.afterlike.catdueller.gui.components.settings.SliderSetting
import org.afterlike.catdueller.gui.components.settings.TextSetting

/**
 * Builds GUI panels from Config.kt settings.
 */
object ConfigPanelBuilder {
    
    fun buildGeneralPanel(panel: Panel) {
        val config = CatDueller.config ?: return
        
        // Current Bot
        panel.addSetting(
            SelectorSetting(
                name = "Current Bot",
                options = listOf("Sumo", "Classic", "OP", "UHC", "Blitz", "Bow"),
                selectedIndex = config.currentBot,
                onChange = { index ->
                    config.currentBot = index
                    config.markDirty()
                    config.writeData()
                    
                    // Actually swap the bot instance
                    val newBot = config.bots[index]
                    if (newBot != null) {
                        CatDueller.swapBot(newBot)
                    }
                }
            )
        )
        
        // Lobby Movement (with sub-setting)
        val lobbyMovementSetting = BooleanSetting(
            name = "Lobby Movement",
            value = config.lobbyMovement,
            onChange = { value ->
                config.lobbyMovement = value
                config.markDirty()
                config.writeData()
            }
        )
        
        lobbyMovementSetting.addSubSetting(
            BooleanSetting(
                name = "Use Recorded Movement",
                value = config.useRecordedMovement,
                onChange = { value ->
                    config.useRecordedMovement = value
                    config.markDirty()
                    config.writeData()
                },
                isSubSetting = true
            )
        )
        
        panel.addSetting(lobbyMovementSetting)
        
        // Combat Logs
        panel.addSetting(
            BooleanSetting(
                name = "Combat Logs",
                value = config.combatLogs,
                onChange = { value ->
                    config.combatLogs = value
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Server IP
        panel.addSetting(
            TextSetting(
                name = "Server IP",
                value = config.serverIP,
                onChange = { value ->
                    config.serverIP = value
                    config.markDirty()
                    config.writeData()
                }
            )
        )

        // Throw After Games
        panel.addSetting(
            SliderSetting(
                name = "Throw After X Games",
                value = config.throwAfterGames.toDouble(),
                min = 0.0,
                max = 1000.0,
                increment = 10.0,
                onChange = { value ->
                    config.throwAfterGames = value.toInt()
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Disconnect After Games
        panel.addSetting(
            SliderSetting(
                name = "Disconnect After Games",
                value = config.disconnectAfterGames.toDouble(),
                min = 0.0,
                max = 10000.0,
                increment = 100.0,
                onChange = { value ->
                    config.disconnectAfterGames = value.toInt()
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Disconnect After Minutes
        panel.addSetting(
            SliderSetting(
                name = "Disconnect After Minutes",
                value = config.disconnectAfterMinutes.toDouble(),
                min = 0.0,
                max = 1440.0,
                increment = 30.0,
                onChange = { value ->
                    config.disconnectAfterMinutes = value.toInt()
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Dynamic Break (with sub-settings)
        val dynamicBreakSetting = BooleanSetting(
            name = "Dynamic Break",
            value = config.autoReconnectAfterDisconnect,
            onChange = { value ->
                config.autoReconnectAfterDisconnect = value
                config.markDirty()
                config.writeData()
            }
        )
        
        dynamicBreakSetting.addSubSetting(
            SliderSetting(
                name = "Dynamic Break Wait (Min)",
                value = config.reconnectWaitMinutes.toDouble(),
                min = 0.0,
                max = 120.0,
                increment = 5.0,
                onChange = { value ->
                    config.reconnectWaitMinutes = value.toInt()
                    config.markDirty()
                    config.writeData()
                },
                scale = 0.85f
            )
        )
        
        dynamicBreakSetting.addSubSetting(
            SliderSetting(
                name = "Dynamic Break Variance",
                value = config.dynamicBreakVariance.toDouble(),
                min = 0.0,
                max = 60.0,
                increment = 5.0,
                onChange = { value ->
                    config.dynamicBreakVariance = value.toInt()
                    config.markDirty()
                    config.writeData()
                },
                scale = 0.85f
            )
        )
        
        dynamicBreakSetting.addSubSetting(
            BooleanSetting(
                name = "Lobby Sit During Break",
                value = config.lobbySitDuringDynamicBreak,
                onChange = { value ->
                    config.lobbySitDuringDynamicBreak = value
                    config.markDirty()
                    config.writeData()
                },
                isSubSetting = true
            )
        )
        
        panel.addSetting(dynamicBreakSetting)
        
        // Pause When Internet Unstable
        panel.addSetting(
            BooleanSetting(
                name = "Pause When Unstable",
                value = config.pauseWhenInternetUnstable,
                onChange = { value ->
                    config.pauseWhenInternetUnstable = value
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Big Break Time (with sub-settings)
        val bigBreakSetting = BooleanSetting(
            name = "Big Break Time",
            value = config.bigBreakEnabled,
            onChange = { value ->
                config.bigBreakEnabled = value
                config.markDirty()
                config.writeData()
            }
        )
        
        bigBreakSetting.addSubSetting(
            SliderSetting(
                name = "Big Break Start Hour",
                value = config.bigBreakStartHour.toDouble(),
                min = 0.0,
                max = 23.0,
                increment = 1.0,
                onChange = { value ->
                    config.bigBreakStartHour = value.toInt()
                    config.markDirty()
                    config.writeData()
                },
                scale = 0.85f
            )
        )
        
        bigBreakSetting.addSubSetting(
            SliderSetting(
                name = "Big Break End Hour",
                value = config.bigBreakEndHour.toDouble(),
                min = 0.0,
                max = 23.0,
                increment = 1.0,
                onChange = { value ->
                    config.bigBreakEndHour = value.toInt()
                    config.markDirty()
                    config.writeData()
                },
                scale = 0.85f
            )
        )
        
        panel.addSetting(bigBreakSetting)

        // Clip Losses
        panel.addSetting(
            BooleanSetting(
                name = "Clip Losses",
                value = config.clipLosses,
                onChange = { value ->
                    config.clipLosses = value
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        
    }
    
    fun buildCombatPanel(panel: Panel) {
        val config = CatDueller.config ?: return
        
        // CPS
        panel.addSetting(
            SliderSetting(
                name = "CPS",
                value = config.cps.toDouble(),
                min = 1.0,
                max = 20.0,
                increment = 0.1,
                onChange = { value ->
                    config.cps = value.toFloat()
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Horizontal Look Speed
        panel.addSetting(
            SliderSetting(
                name = "Horizontal Look Speed",
                value = config.lookSpeedHorizontal.toDouble(),
                min = 5.0,
                max = 30.0,
                increment = 1.0,
                onChange = { value ->
                    config.lookSpeedHorizontal = value.toInt()
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Vertical Look Speed
        panel.addSetting(
            SliderSetting(
                name = "Vertical Look Speed",
                value = config.lookSpeedVertical.toDouble(),
                min = 1.0,
                max = 20.0,
                increment = 1.0,
                onChange = { value ->
                    config.lookSpeedVertical = value.toInt()
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Look Randomization
        panel.addSetting(
            SliderSetting(
                name = "Look Randomization",
                value = config.lookRand.toDouble(),
                min = 0.0,
                max = 2.0,
                increment = 0.1,
                onChange = { value ->
                    config.lookRand = value.toFloat()
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Vertical Multipoint
        panel.addSetting(
            BooleanSetting(
                name = "Vertical Multipoint",
                value = config.verticalMultipoint,
                onChange = { value ->
                    config.verticalMultipoint = value
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Disable Aiming
        panel.addSetting(
            BooleanSetting(
                name = "Disable Aiming",
                value = config.disableAiming,
                onChange = { value ->
                    config.disableAiming = value
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Max Look Distance
        panel.addSetting(
            SliderSetting(
                name = "Max Look Distance",
                value = config.maxDistanceLook.toDouble(),
                min = 120.0,
                max = 180.0,
                increment = 5.0,
                onChange = { value ->
                    config.maxDistanceLook = value.toInt()
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Max Attack Distance
        panel.addSetting(
            SliderSetting(
                name = "Max Attack Distance",
                value = config.maxDistanceAttack.toDouble(),
                min = 3.0,
                max = 15.0,
                increment = 1.0,
                onChange = { value ->
                    config.maxDistanceAttack = value.toInt()
                    config.markDirty()
                    config.writeData()
                }
            )
        )

        // Enable W-Tap (with sub-settings)
        val wTapSetting = BooleanSetting(
            name = "Enable W-Tap",
            value = config.enableWTap,
            onChange = { value ->
                config.enableWTap = value
                config.markDirty()
                config.writeData()
            }
        )
        
        wTapSetting.addSubSetting(
            SliderSetting(
                name = "W-Tap Delay",
                value = config.wTapDelay.toDouble(),
                min = 0.0,
                max = 300.0,
                increment = 25.0,
                onChange = { value ->
                    config.wTapDelay = value.toInt()
                    config.markDirty()
                    config.writeData()
                },
                scale = 0.85f
            )
        )
        
        wTapSetting.addSubSetting(
            BooleanSetting(
                name = "Sprint Reset (No Stop)",
                value = config.sprintReset,
                onChange = { value ->
                    config.sprintReset = value
                    config.markDirty()
                    config.writeData()
                },
                isSubSetting = true
            )
        )
        
        panel.addSetting(wTapSetting)
        
        // Hold Left Click
        panel.addSetting(
            BooleanSetting(
                name = "Hold Left Click",
                value = config.holdLeftClick,
                onChange = { value ->
                    config.holdLeftClick = value
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Hit Select (with sub-settings)
        val hitSelectSetting = BooleanSetting(
            name = "Hit Select",
            value = config.hitSelect,
            onChange = { value ->
                config.hitSelect = value
                config.markDirty()
                config.writeData()
            }
        )
        
        hitSelectSetting.addSubSetting(
            SliderSetting(
                name = "Hit Select Pause Duration",
                value = config.hitSelectDelay.toDouble(),
                min = 0.0,
                max = 500.0,
                increment = 50.0,
                onChange = { value ->
                    config.hitSelectDelay = value.toInt()
                    config.markDirty()
                    config.writeData()
                },
                scale = 0.85f
            )
        )
        
        hitSelectSetting.addSubSetting(
            SliderSetting(
                name = "Hit Later In Trades",
                value = config.hitLaterInTrades.toDouble(),
                min = 0.0,
                max = 500.0,
                increment = 50.0,
                onChange = { value ->
                    config.hitLaterInTrades = value.toInt()
                    config.markDirty()
                    config.writeData()
                },
                scale = 0.85f
            )
        )
        
        hitSelectSetting.addSubSetting(
            SliderSetting(
                name = "Hit Select Cancel Rate",
                value = config.hitSelectCancelRate.toDouble(),
                min = 0.0,
                max = 100.0,
                increment = 5.0,
                onChange = { value ->
                    config.hitSelectCancelRate = value.toInt()
                    config.markDirty()
                    config.writeData()
                },
                scale = 0.85f
            )
        )
        
        hitSelectSetting.addSubSetting(
            SliderSetting(
                name = "Missed Hits Cancel Rate",
                value = config.missedHitsCancelRate.toDouble(),
                min = 0.0,
                max = 100.0,
                increment = 5.0,
                onChange = { value ->
                    config.missedHitsCancelRate = value.toInt()
                    config.markDirty()
                    config.writeData()
                },
                scale = 0.85f
            )
        )
        
        panel.addSetting(hitSelectSetting)
        
        // Wait For First Hit (with sub-setting)
        val waitFirstHitSetting = BooleanSetting(
            name = "Wait For First Hit",
            value = config.waitForFirstHit,
            onChange = { value ->
                config.waitForFirstHit = value
                config.markDirty()
                config.writeData()
            }
        )
        
        waitFirstHitSetting.addSubSetting(
            SliderSetting(
                name = "Wait For First Hit Timeout",
                value = config.waitForFirstHitTimeout.toDouble(),
                min = 50.0,
                max = 500.0,
                increment = 50.0,
                onChange = { value ->
                    config.waitForFirstHitTimeout = value.toInt()
                    config.markDirty()
                    config.writeData()
                },
                scale = 0.85f
            )
        )
        
        panel.addSetting(waitFirstHitSetting)
        
        // Hurt Strafe
        panel.addSetting(
            BooleanSetting(
                name = "Hurt Strafe",
                value = config.hurtStrafe,
                onChange = { value ->
                    config.hurtStrafe = value
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Jump Reset
        panel.addSetting(
            SliderSetting(
                name = "Jump Reset",
                value = config.jumpVelocity.toDouble(),
                min = 0.0,
                max = 100.0,
                increment = 5.0,
                onChange = { value ->
                    config.jumpVelocity = value.toInt()
                    config.markDirty()
                    config.writeData()
                }
            )
        )
    }
    
    
    fun buildClassicPanel(panel: Panel) {
        val config = CatDueller.config ?: return
        
        // Prediction Ticks Bonus
        panel.addSetting(
            SliderSetting(
                name = "Projectiles Delayed Ticks",
                value = config.predictionTicksBonus.toDouble(),
                min = 0.0,
                max = 10.0,
                increment = 1.0,
                onChange = { value ->
                    config.predictionTicksBonus = value.toInt()
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Counter Strafe Multiplier
        panel.addSetting(
            SliderSetting(
                name = "Counter Strafe Multiplier",
                value = config.counterStrafeBonus.toDouble(),
                min = 0.5,
                max = 3.0,
                increment = 0.1,
                onChange = { value ->
                    config.counterStrafeBonus = value.toFloat()
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Enable Rod Jump
        panel.addSetting(
            BooleanSetting(
                name = "Enable Rod Trick",
                value = config.enableRodJump,
                onChange = { value ->
                    config.enableRodJump = value
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Rod Jump Delay
        panel.addSetting(
            SliderSetting(
                name = "Rod Trick Delay",
                value = config.rodJumpDelay.toDouble(),
                min = 0.0,
                max = 500.0,
                increment = 25.0,
                onChange = { value ->
                    config.rodJumpDelay = value.toInt()
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Enable Retreat
        panel.addSetting(
            BooleanSetting(
                name = "Enable Retreat",
                value = config.enableRetreat,
                onChange = { value ->
                    config.enableRetreat = value
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Enable Arrow Blocking
        panel.addSetting(
            BooleanSetting(
                name = "Enable Arrow Blocking",
                value = config.enableArrowBlocking,
                onChange = { value ->
                    config.enableArrowBlocking = value
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Dodge Arrows
        panel.addSetting(
            BooleanSetting(
                name = "Dodge Arrows",
                value = config.dodgeArrow,
                onChange = { value ->
                    config.dodgeArrow = value
                    config.markDirty()
                    config.writeData()
                }
            )
        )
    }
    
    fun buildSumoPanel(panel: Panel) {
        val config = CatDueller.config ?: return
        
        // Hit Select At Edge (with sub-setting)
        val hitSelectEdgeSetting = BooleanSetting(
            name = "Hit Select At Edge",
            value = config.hitSelectAtEdge,
            onChange = { value ->
                config.hitSelectAtEdge = value
                config.markDirty()
                config.writeData()
            }
        )
        
        hitSelectEdgeSetting.addSubSetting(
            BooleanSetting(
                name = "Jump When Hit Selecting",
                value = config.distance7Jump,
                onChange = { value ->
                    config.distance7Jump = value
                    config.markDirty()
                    config.writeData()
                },
                isSubSetting = true
            )
        )
        
        panel.addSetting(hitSelectEdgeSetting)
        
        // S Tap (with sub-setting)
        val sTapSetting = BooleanSetting(
            name = "S Tap",
            value = config.sTap,
            onChange = { value ->
                config.sTap = value
                config.markDirty()
                config.writeData()
            }
        )
        
        sTapSetting.addSubSetting(
            SliderSetting(
                name = "S Tap Distance",
                value = config.sTapDistance.toDouble(),
                min = 2.0,
                max = 6.0,
                increment = 0.1,
                onChange = { value ->
                    config.sTapDistance = value.toFloat()
                    config.markDirty()
                    config.writeData()
                },
                scale = 0.85f
            )
        )
        
        panel.addSetting(sTapSetting)
        
        // Stop When Opponent At Edge (with sub-setting)
        val stopAtEdgeSetting = BooleanSetting(
            name = "Stop When Opponent At Edge",
            value = config.stopWhenOpponentAtEdge,
            onChange = { value ->
                config.stopWhenOpponentAtEdge = value
                config.markDirty()
                config.writeData()
            }
        )
        
        stopAtEdgeSetting.addSubSetting(
            SliderSetting(
                name = "Stop At Edge Duration",
                value = config.stopAtEdgeDuration.toDouble(),
                min = 100.0,
                max = 2000.0,
                increment = 100.0,
                onChange = { value ->
                    config.stopAtEdgeDuration = value.toInt()
                    config.markDirty()
                    config.writeData()
                },
                scale = 0.85f
            )
        )
        
        panel.addSetting(stopAtEdgeSetting)
        
        // Freeze When Off Edge (with sub-setting)
        val freezeOffEdgeSetting = BooleanSetting(
            name = "Freeze When Off Edge",
            value = config.freezeWhenOffEdge,
            onChange = { value ->
                config.freezeWhenOffEdge = value
                config.markDirty()
                config.writeData()
            }
        )
        
        freezeOffEdgeSetting.addSubSetting(
            TextSetting(
                name = "Freeze Bind",
                value = config.freezeBind,
                onChange = { value ->
                    config.freezeBind = value
                    config.markDirty()
                    config.writeData()
                },
                scale = 0.85f
            )
        )
        
        panel.addSetting(freezeOffEdgeSetting)
        
        // Sumo Long Jump
        panel.addSetting(
            BooleanSetting(
                name = "Sumo Long Jump",
                value = config.sumoLongJump,
                onChange = { value ->
                    config.sumoLongJump = value
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Random Strafe
        panel.addSetting(
            BooleanSetting(
                name = "Random Strafe",
                value = config.randomStrafe,
                onChange = { value ->
                    config.randomStrafe = value
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Enable Strafe Switch (with sub-setting)
        val strafeSwitchSetting = BooleanSetting(
            name = "Enable Strafe Switch",
            value = config.enableStrafeSwitch,
            onChange = { value ->
                config.enableStrafeSwitch = value
                config.markDirty()
                config.writeData()
            }
        )
        
        strafeSwitchSetting.addSubSetting(
            SliderSetting(
                name = "Strafe Switch Delay",
                value = config.strafeSwitchDelay.toDouble(),
                min = 0.0,
                max = 1500.0,
                increment = 100.0,
                onChange = { value ->
                    config.strafeSwitchDelay = value.toInt()
                    config.markDirty()
                    config.writeData()
                },
                scale = 0.85f
            )
        )
        
        panel.addSetting(strafeSwitchSetting)
    }
    
    fun buildTogglingPanel(panel: Panel) {
        val config = CatDueller.config ?: return
        
        // Blink At Edge (with sub-setting)
        val blinkAtEdgeSetting = BooleanSetting(
            name = "Blink At Edge",
            value = config.blinkAtEdge,
            onChange = { value ->
                config.blinkAtEdge = value
                config.markDirty()
                config.writeData()
            }
        )
        
        blinkAtEdgeSetting.addSubSetting(
            TextSetting(
                name = "Blink Key",
                value = config.blinkKey,
                onChange = { value ->
                    config.blinkKey = value
                    config.markDirty()
                    config.writeData()
                },
                scale = 0.85f
            )
        )
        
        panel.addSetting(blinkAtEdgeSetting)
        
        // Toggle Blatant at Edge (with sub-settings)
        val toggleBlatantEdgeSetting = BooleanSetting(
            name = "Toggle Blatant at Edge",
            value = config.toggleBlatantAtEdge,
            onChange = { value ->
                config.toggleBlatantAtEdge = value
                config.markDirty()
                config.writeData()
            }
        )
        
        toggleBlatantEdgeSetting.addSubSetting(
            SliderSetting(
                name = "Toggle Blatant Distance",
                value = config.toggleBlatantDistance.toDouble(),
                min = 1.0,
                max = 10.0,
                increment = 0.1,
                onChange = { value ->
                    config.toggleBlatantDistance = value.toFloat()
                    config.markDirty()
                    config.writeData()
                },
                scale = 0.85f
            )
        )
        
        toggleBlatantEdgeSetting.addSubSetting(
            TextSetting(
                name = "Blatant Toggle Key",
                value = config.blatantToggleKey,
                onChange = { value ->
                    config.blatantToggleKey = value
                    config.markDirty()
                    config.writeData()
                },
                scale = 0.85f
            )
        )
        
        panel.addSetting(toggleBlatantEdgeSetting)
        
        // Toggle Blatant on Blacklisted (with sub-settings)
        val toggleBlatantBlacklistSetting = BooleanSetting(
            name = "Toggle Blatant on Blacklisted",
            value = config.toggleBlatantOnBlacklisted,
            onChange = { value ->
                config.toggleBlatantOnBlacklisted = value
                config.markDirty()
                config.writeData()
            }
        )
        
        toggleBlatantBlacklistSetting.addSubSetting(
            TextSetting(
                name = "Blatant Toggle Key",
                value = config.blatantToggleKey,
                onChange = { value ->
                    config.blatantToggleKey = value
                    config.markDirty()
                    config.writeData()
                },
                scale = 0.85f
            )
        )
        
        toggleBlatantBlacklistSetting.addSubSetting(
            TextSetting(
                name = "Blacklisted Players",
                value = config.blacklistedPlayers,
                onChange = { value ->
                    config.blacklistedPlayers = value
                    config.markDirty()
                    config.writeData()
                },
                scale = 0.85f
            )
        )
        
        panel.addSetting(toggleBlatantBlacklistSetting)
    }
    
    fun buildQueueDodgingPanel(panel: Panel) {
        val config = CatDueller.config ?: return
        
        // Dodge Standing Still
        panel.addSetting(
            BooleanSetting(
                name = "Dodge Standing Still",
                value = config.dodgeStandingStill,
                onChange = { value ->
                    config.dodgeStandingStill = value
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Dodge Huaxi
        panel.addSetting(
            BooleanSetting(
                name = "Dodge Huaxi",
                value = config.dodgeHuaxi,
                onChange = { value ->
                    config.dodgeHuaxi = value
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Dodge Particle Type
        panel.addSetting(
            SelectorSetting(
                name = "Dodge Particle Type",
                options = listOf("None", "Slime", "Portal", "Rainbow", "Heart", "Angry Villager"),
                selectedIndex = config.dodgeParticleType,
                onChange = { index ->
                    config.dodgeParticleType = index
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Guild Dodge
        panel.addSetting(
            BooleanSetting(
                name = "Guild Dodge",
                value = config.guildDodge,
                onChange = { value ->
                    config.guildDodge = value
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Send Server ID to Guild
        panel.addSetting(
            BooleanSetting(
                name = "Send Server ID to Guild",
                value = config.sendServerToGuild,
                onChange = { value ->
                    config.sendServerToGuild = value
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // DM Dodge
        panel.addSetting(
            BooleanSetting(
                name = "DM Dodge",
                value = config.dmDodge,
                onChange = { value ->
                    config.dmDodge = value
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Send Server ID to DM (with sub-setting)
        val sendServerDMSetting = BooleanSetting(
            name = "Send Server ID to DM",
            value = config.sendServerToDM,
            onChange = { value ->
                config.sendServerToDM = value
                config.markDirty()
                config.writeData()
            }
        )
        
        sendServerDMSetting.addSubSetting(
            TextSetting(
                name = "DM Target Player",
                value = config.dmTargetPlayer,
                onChange = { value ->
                    config.dmTargetPlayer = value
                    config.markDirty()
                    config.writeData()
                },
                scale = 0.85f
            )
        )
        
        panel.addSetting(sendServerDMSetting)
        
        // IRC Dodge
        panel.addSetting(
            BooleanSetting(
                name = "IRC Dodge",
                value = config.ircDodgeEnabled,
                onChange = { value ->
                    config.ircDodgeEnabled = value
                    config.markDirty()
                    config.writeData()
                }
            )
        )
    }
    
    fun buildAutoRequeuePanel(panel: Panel) {
        val config = CatDueller.config ?: return
        
        // Auto Requeue Delay
        panel.addSetting(
            SliderSetting(
                name = "Auto Requeue Delay",
                value = config.autoRqDelay.toDouble(),
                min = 500.0,
                max = 5000.0,
                increment = 50.0,
                onChange = { value ->
                    config.autoRqDelay = value.toInt()
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Requeue After No Game
        panel.addSetting(
            SliderSetting(
                name = "Requeue After No Game",
                value = config.rqNoGame.toDouble(),
                min = 15.0,
                max = 60.0,
                increment = 5.0,
                onChange = { value ->
                    config.rqNoGame = value.toInt()
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Paper Requeue
        panel.addSetting(
            BooleanSetting(
                name = "Paper Requeue",
                value = config.paperRequeue,
                onChange = { value ->
                    config.paperRequeue = value
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Fast Requeue
        panel.addSetting(
            BooleanSetting(
                name = "Fast Requeue",
                value = config.fastRequeue,
                onChange = { value ->
                    config.fastRequeue = value
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Force Requeue
        panel.addSetting(
            BooleanSetting(
                name = "Force Requeue",
                value = config.forceRequeue,
                onChange = { value ->
                    config.forceRequeue = value
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Delay Requeue After Losing (with sub-setting)
        val delayRequeueSetting = BooleanSetting(
            name = "Delay Requeue After Losing",
            value = config.delayRequeueAfterLosing,
            onChange = { value ->
                config.delayRequeueAfterLosing = value
                config.markDirty()
                config.writeData()
            }
        )
        
        delayRequeueSetting.addSubSetting(
            SliderSetting(
                name = "Losing Requeue Delay",
                value = config.losingRequeueDelay.toDouble(),
                min = 1.0,
                max = 30.0,
                increment = 1.0,
                onChange = { value ->
                    config.losingRequeueDelay = value.toInt()
                    config.markDirty()
                    config.writeData()
                },
                scale = 0.85f
            )
        )
        
        panel.addSetting(delayRequeueSetting)
    }
    
    fun buildAutoGGPanel(panel: Panel) {
        val config = CatDueller.config ?: return
        
        // Enable AutoGG (with sub-settings)
        val autoGGSetting = BooleanSetting(
            name = "Enable AutoGG",
            value = config.sendAutoGG,
            onChange = { value ->
                config.sendAutoGG = value
                config.markDirty()
                config.writeData()
            }
        )
        
        // Add sub-settings for AutoGG
        autoGGSetting.addSubSetting(
            TextSetting(
                name = "AutoGG Message",
                value = config.ggMessage,
                onChange = { value ->
                    config.ggMessage = value
                    config.markDirty()
                    config.writeData()
                },
                scale = 0.85f
            )
        )
        
        autoGGSetting.addSubSetting(
            SliderSetting(
                name = "AutoGG Delay",
                value = config.ggDelay.toDouble(),
                min = 50.0,
                max = 1000.0,
                increment = 50.0,
                onChange = { value ->
                    config.ggDelay = value.toInt()
                    config.markDirty()
                    config.writeData()
                },
                scale = 0.85f
            )
        )
        
        panel.addSetting(autoGGSetting)
        
        // Game Start Message (with sub-settings)
        val startMessageSetting = BooleanSetting(
            name = "Game Start Message",
            value = config.sendStartMessage,
            onChange = { value ->
                config.sendStartMessage = value
                config.markDirty()
                config.writeData()
            }
        )
        
        startMessageSetting.addSubSetting(
            TextSetting(
                name = "Start Message",
                value = config.startMessage,
                onChange = { value ->
                    config.startMessage = value
                    config.markDirty()
                    config.writeData()
                },
                scale = 0.85f
            )
        )
        
        startMessageSetting.addSubSetting(
            SliderSetting(
                name = "Start Message Delay",
                value = config.startMessageDelay.toDouble(),
                min = 50.0,
                max = 1000.0,
                increment = 50.0,
                onChange = { value ->
                    config.startMessageDelay = value.toInt()
                    config.markDirty()
                    config.writeData()
                },
                scale = 0.85f
            )
        )
        
        panel.addSetting(startMessageSetting)
    }
    
    fun buildChatMessagesPanel(panel: Panel) {
        val config = CatDueller.config ?: return
        
        // Enable Taunt Messages (with sub-setting)
        val tauntSetting = BooleanSetting(
            name = "Enable Taunt Messages",
            value = config.enableTauntMessages,
            onChange = { value ->
                config.enableTauntMessages = value
                config.markDirty()
                config.writeData()
            }
        )
        
        tauntSetting.addSubSetting(
            SliderSetting(
                name = "Taunt Threshold (Seconds)",
                value = config.tauntThresholdSeconds.toDouble(),
                min = 10.0,
                max = 120.0,
                increment = 5.0,
                onChange = { value ->
                    config.tauntThresholdSeconds = value.toInt()
                    config.markDirty()
                    config.writeData()
                },
                scale = 0.85f
            )
        )
        
        panel.addSetting(tauntSetting)
        
        // ? ok
        panel.addSetting(
            BooleanSetting(
                name = "? ok",
                value = config.autoReplyDM,
                onChange = { value ->
                    config.autoReplyDM = value
                    config.markDirty()
                    config.writeData()
                }
            )
        )
        
        // Anti Ragebait
        panel.addSetting(
            BooleanSetting(
                name = "Anti Ragebait",
                value = config.antiRagebait,
                onChange = { value ->
                    config.antiRagebait = value
                    config.markDirty()
                    config.writeData()
                }
            )
        )
    }
    
    fun buildWebhookPanel(panel: Panel) {
        val config = CatDueller.config ?: return
        
        // Send Webhook Messages (with sub-setting)
        val webhookSetting = BooleanSetting(
            name = "Send Webhook Messages",
            value = config.sendWebhookMessages,
            onChange = { value ->
                config.sendWebhookMessages = value
                config.markDirty()
                config.writeData()
            }
        )
        
        webhookSetting.addSubSetting(
            TextSetting(
                name = "Discord Webhook URL",
                value = config.webhookURL,
                onChange = { value ->
                    config.webhookURL = value
                    config.markDirty()
                    config.writeData()
                },
                scale = 0.85f
            )
        )
        
        panel.addSetting(webhookSetting)
    }
    
    fun buildBotCrasherPanel(panel: Panel) {
        val config = CatDueller.config ?: return
        
        // Bot Crasher Mode (with sub-settings)
        val botCrasherSetting = BooleanSetting(
            name = "Bot Crasher Mode",
            value = config.botCrasherMode,
            onChange = { value ->
                config.botCrasherMode = value
                config.markDirty()
                config.writeData()
            }
        )
        
        botCrasherSetting.addSubSetting(
            BooleanSetting(
                name = "Auto Requeue",
                value = config.botCrasherAutoRequeue,
                onChange = { value ->
                    config.botCrasherAutoRequeue = value
                    config.markDirty()
                    config.writeData()
                },
                isSubSetting = true
            )
        )
        
        botCrasherSetting.addSubSetting(
            BooleanSetting(
                name = "Spam Players",
                value = config.botCrasherSpamPlayers,
                onChange = { value ->
                    config.botCrasherSpamPlayers = value
                    config.markDirty()
                    config.writeData()
                },
                isSubSetting = true
            )
        )
        
        botCrasherSetting.addSubSetting(
            TextSetting(
                name = "Target Players",
                value = config.botCrasherTargetPlayers,
                onChange = { value ->
                    config.botCrasherTargetPlayers = value
                    config.markDirty()
                    config.writeData()
                },
                scale = 0.85f
            )
        )
        
        panel.addSetting(botCrasherSetting)
    }
}
