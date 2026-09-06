package su.terrafirmagreg.core.mixins.common.tfc.solar;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.dries007.tfc.util.calendar.Calendar;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;

import su.terrafirmagreg.core.compat.tfc.solar.CalendarExtension;
import su.terrafirmagreg.core.compat.tfc.solar.SolarCalendarBackport;

@Mixin(value = Calendar.class, remap = false)
public abstract class CalendarMixin implements CalendarExtension {
    @Shadow
    protected long playerTicks;

    @Shadow
    protected long calendarTicks;

    @Shadow
    protected boolean doDaylightCycle;

    @Shadow
    protected boolean arePlayersLoggedOn;

    @Shadow
    protected int daysInMonth;

    @Unique
    private float tfg$calendarTickRate = 1f;

    @Unique
    private float tfg$calendarPartialTick = 0f;

    @Override
    public float tfg$getCalendarTickRate() {
        return tfg$calendarTickRate;
    }

    @Override
    public void tfg$setCalendarTickRate(float rate) {
        this.tfg$calendarTickRate = rate;
    }

    @Override
    public float tfg$getCalendarPartialTick() {
        return tfg$calendarPartialTick;
    }

    @Override
    public void tfg$setCalendarPartialTick(float partialTick) {
        this.tfg$calendarPartialTick = partialTick;
    }

    @Unique
    public void tfg$advanceCalendarTick() {
        tfg$calendarPartialTick += tfg$calendarTickRate;
        calendarTicks += Mth.floor(tfg$calendarPartialTick);
        tfg$calendarPartialTick = Mth.frac(tfg$calendarPartialTick);
    }

    @Unique
    public long tfg$getPlayerTicks() {
        return playerTicks;
    }

    @Unique
    public void tfg$addPlayerTicks(long amount) {
        playerTicks += amount;
    }

    @Unique
    public long tfg$getCalendarTicksField() {
        return calendarTicks;
    }

    @Unique
    public void tfg$setCalendarTicksField(long value) {
        calendarTicks = value;
    }

    @Unique
    public void tfg$addCalendarTicksField(long amount) {
        calendarTicks += amount;
    }

    @Unique
    public boolean tfg$getDoDaylightCycle() {
        return doDaylightCycle;
    }

    @Unique
    public void tfg$setDoDaylightCycle(boolean value) {
        doDaylightCycle = value;
    }

    @Unique
    public boolean tfg$getArePlayersLoggedOn() {
        return arePlayersLoggedOn;
    }

    @Unique
    public void tfg$setArePlayersLoggedOn(boolean value) {
        arePlayersLoggedOn = value;
    }

    @Unique
    public int tfg$getDaysInMonth() {
        return daysInMonth;
    }

    @Unique
    public void tfg$setDaysInMonth(int value) {
        daysInMonth = value;
    }

    @Inject(method = "write()Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"), remap = false)
    private void tfg$writeNbt(CallbackInfoReturnable<CompoundTag> cir) {
        if (!SolarCalendarBackport.isEnabled()) {
            return;
        }
        cir.getReturnValue().putFloat("calendarTickRate", tfg$calendarTickRate);
        cir.getReturnValue().putFloat("calendarPartialTick", tfg$calendarPartialTick);
    }

    @Inject(method = "read(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"), remap = false)
    private void tfg$readNbt(@Nullable CompoundTag nbt, CallbackInfo ci) {
        if (!SolarCalendarBackport.isEnabled() || nbt == null) {
            return;
        }
        if (nbt.contains("calendarTickRate")) {
            tfg$calendarTickRate = nbt.getFloat("calendarTickRate");
            tfg$calendarPartialTick = nbt.getFloat("calendarPartialTick");
        } else {
            tfg$calendarTickRate = 1.0f;
            tfg$calendarPartialTick = 0f;
        }
    }

    @Inject(method = "write(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("TAIL"), remap = false)
    private void tfg$writePacket(FriendlyByteBuf buffer, CallbackInfo ci) {
        if (!SolarCalendarBackport.isEnabled()) {
            return;
        }
        buffer.writeFloat(tfg$calendarTickRate);
        buffer.writeFloat(tfg$calendarPartialTick);
    }

    @Inject(method = "read(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("TAIL"), remap = false)
    private void tfg$readPacket(FriendlyByteBuf buffer, CallbackInfo ci) {
        if (!SolarCalendarBackport.isEnabled()) {
            return;
        }
        tfg$calendarTickRate = buffer.readFloat();
        tfg$calendarPartialTick = buffer.readFloat();
    }

    @Inject(method = "resetTo", at = @At("TAIL"), remap = false)
    private void tfg$resetTo(Calendar resetTo, CallbackInfo ci) {
        if (!SolarCalendarBackport.isEnabled() || !(resetTo instanceof CalendarExtension extension)) {
            return;
        }
        tfg$calendarTickRate = extension.tfg$getCalendarTickRate();
        tfg$calendarPartialTick = extension.tfg$getCalendarPartialTick();
    }

    @Inject(method = "resetToDefault", at = @At("TAIL"), remap = false)
    private void tfg$resetToDefault(CallbackInfo ci) {
        if (!SolarCalendarBackport.isEnabled()) {
            return;
        }
        tfg$calendarTickRate = SolarCalendarBackport.defaultTickRateForNewWorlds();
        tfg$calendarPartialTick = 0f;
        doDaylightCycle = false;
    }
}
