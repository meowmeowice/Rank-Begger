package org.afterlike.catdueller.mixins;

import com.google.common.collect.Lists;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityParticleEmitter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.ConcurrentModificationException;
import java.util.List;

/**
 * Mixin for {@link EffectRenderer} to fix a concurrent modification crash.
 *
 * <p>This mixin addresses a race condition that can occur when particle emitters
 * are modified while being iterated over during effect updates.</p>
 */
@Mixin(EffectRenderer.class)
public abstract class MixinEffectRenderer {

    /**
     * Shadow method to update particles in a specific effect layer.
     *
     * @param layer The layer index (0-3) to update.
     */
    @Shadow
    protected abstract void updateEffectLayer(int layer);

    /**
     * Shadow field containing the list of active particle emitters.
     */
    @Shadow
    private List<EntityParticleEmitter> particleEmitters;

    /**
     * Updates all particle effects and emitters.
     *
     * <p>This method overwrites the vanilla implementation to wrap the update logic
     * in a try-catch block, gracefully handling {@link ConcurrentModificationException}
     * that can occur when the particle emitter list is modified during iteration.</p>
     *
     * @author Mojang
     * @reason Fix ConcurrentModificationException crash
     */
    @Overwrite
    public void updateEffects() {
        try {
            for (int i = 0; i < 4; ++i) {
                this.updateEffectLayer(i);
            }

            List<EntityParticleEmitter> list = Lists.newArrayList();

            for (EntityParticleEmitter entityparticleemitter : this.particleEmitters) {
                entityparticleemitter.onUpdate();

                if (entityparticleemitter.isDead) {
                    list.add(entityparticleemitter);
                }
            }

            this.particleEmitters.removeAll(list);
        } catch (final ConcurrentModificationException ignored) {
        }
    }

}