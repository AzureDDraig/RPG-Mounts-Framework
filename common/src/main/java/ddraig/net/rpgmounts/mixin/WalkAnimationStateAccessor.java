package ddraig.net.rpgmounts.mixin;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Mixin Accessor for WalkAnimationState
 * Allows accessing and modifying private animation variables.
 * 
 * Change Log:
 * - 2026-06-19: [Initial Creation] - Added accessors for speedOld, speed, and position.
 */
@Mixin(WalkAnimationState.class)
public interface WalkAnimationStateAccessor {
    @Accessor("speedOld")
    float getSpeedOld();

    @Accessor("speedOld")
    void setSpeedOld(float speedOld);

    @Accessor("speed")
    float getSpeed();

    @Accessor("speed")
    void setSpeed(float speed);

    @Accessor("position")
    float getPosition();

    @Accessor("position")
    void setPosition(float position);
}
