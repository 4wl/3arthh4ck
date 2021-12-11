package me.earth.earthhack.impl.modules.combat.autocrystal;

import me.earth.earthhack.api.cache.SettingCache;
import me.earth.earthhack.api.setting.Setting;
import me.earth.earthhack.api.setting.settings.NumberSetting;
import me.earth.earthhack.impl.managers.Managers;
import me.earth.earthhack.impl.modules.Caches;
import me.earth.earthhack.impl.modules.client.safety.Safety;
import me.earth.earthhack.impl.modules.combat.autocrystal.util.BreakData;
import me.earth.earthhack.impl.modules.combat.autocrystal.util.CrystalData;
import me.earth.earthhack.impl.util.math.MathUtil;
import me.earth.earthhack.impl.util.minecraft.entity.EntityUtil;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.util.CombatRules;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Explosion;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class HelperBreak extends AbstractBreakHelper<CrystalData>
{
    private static final SettingCache<Float, NumberSetting<Float>, Safety> MD =
        Caches.getSetting(Safety.class, Setting.class, "MaxDamage", 4.0f);

    public HelperBreak(AutoCrystal module)
    {
        super(module);
    }

    @Override
    public BreakData<CrystalData> newData(Collection<CrystalData> data)
    {
        return new BreakData<>(data);
    }

    @Override
    protected CrystalData newCrystalData(Entity crystal)
    {
        return new CrystalData(crystal);
    }

    @Override
    protected boolean isValid(Entity crystal, CrystalData data)
    {
        double distance = Managers.POSITION.getDistanceSq(crystal);
        if (distance > MathUtil.square(module.breakTrace.getValue())
                && !Managers.POSITION.canEntityBeSeen(crystal))
        {
            return false;
        }

        return distance <= MathUtil.square(module.breakRange.getValue());
    }

    @Override
    protected boolean calcSelf(Entity crystal, CrystalData data)
    {
        float selfDamage = module.damageHelper.getDamage(crystal);
        data.setSelfDmg(selfDamage);
        if (selfDamage > EntityUtil.getHealth(mc.player) - 1.0f)
        {
            Managers.SAFETY.setSafe(false);
            if (!module.suicide.getValue())
            {
                return true;
            }
        }

        if (selfDamage > MD.getValue())
        {
            Managers.SAFETY.setSafe(false);
        }

        return false;
    }

    @Override
    protected void calcCrystal(BreakData<CrystalData> data,
                               CrystalData crystalData,
                               Entity crystal,
                               List<EntityPlayer> players)
    {
        boolean highSelf = crystalData.getSelfDmg()
                                > module.maxSelfBreak.getValue();

        if (!module.suicide.getValue()
                && !module.overrideBreak.getValue()
                && highSelf)
        {
            return;
        }

        float damage = 0.0f;
        boolean killing = false;
        for (EntityPlayer player : players)
        {
            if (player.getDistanceSq(crystal) > 144)
            {
                continue;
            }

            float playerDamage = module.damageHelper.getDamage(crystal, player);
            if (playerDamage > crystalData.getDamage())
            {
                crystalData.setDamage(playerDamage);
            }

            if (playerDamage > EntityUtil.getHealth(player) + 1.0f)
            {
                killing = true;
                highSelf = false;
            }

            if (playerDamage > damage)
            {
                damage = playerDamage;
            }
        }

        if (module.antiTotem.getValue()
                && crystal.getPosition()
                          .down()
                          .equals(module.antiTotemHelper.getTargetPos()))
        {
            data.setAntiTotem(crystal);
        }

        if (!highSelf && (!module.efficient.getValue()
                            || damage > crystalData.getSelfDmg()
                            || killing))
        {
            data.register(crystalData);
        }
    }

    public float calcDmg(BlockPos b, EntityPlayer target) {
        return calculateDamage(b.getX() + .5, b.getY() + 1, b.getZ() + .5, target);
    }

    private float calculateDamage(double posX, double posY, double posZ, Entity entity) {
        float doubleExplosionSize = 12.0f;
        double distancedsize = entity.getDistance(posX, posY, posZ) / 12.0;
        Vec3d vec3d = new Vec3d(posX, posY, posZ);
        double blockDensity = 0.0;
        try {
            blockDensity = entity.world.getBlockDensity(vec3d, entity.getEntityBoundingBox());
        } catch (Exception exception) {

        }
        double v = (1.0 - distancedsize) * blockDensity;
        float damage = (int) ((v * v + v) / 2.0 * 7.0 * 12.0 + 1.0);
        double finald = 1.0;
        if (entity instanceof EntityLivingBase) {
            finald = this.getBlastReduction((EntityLivingBase) entity, this.getDamageMultiplied(damage), new Explosion(AutoCrystal.mc.world, null, posX, posY, posZ, 6.0f, false, true));
        }
        return (float) finald;
    }

    private float getBlastReduction(EntityLivingBase entity, float damageI, Explosion explosion) {
        float damage = damageI;
        if (entity instanceof EntityPlayer) {
            EntityPlayer ep = (EntityPlayer) entity;
            DamageSource ds = DamageSource.causeExplosionDamage(explosion);
            damage = CombatRules.getDamageAfterAbsorb(damage, (float) ep.getTotalArmorValue(), (float) ep.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS).getAttributeValue());
            int k = 0;
            try {
                k = EnchantmentHelper.getEnchantmentModifierDamage(ep.getArmorInventoryList(), ds);
            } catch (Exception exception) {

            }
            float f = MathHelper.clamp((float) k, 0.0f, 20.0f);
            damage *= 1.0f - f / 25.0f;
            if (entity.isPotionActive(MobEffects.RESISTANCE)) {
                damage -= damage / 4.0f;
            }
            damage = Math.max(damage, 0.0f);
            return damage;
        }
        damage = CombatRules.getDamageAfterAbsorb(damage, (float) entity.getTotalArmorValue(), (float) entity.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS).getAttributeValue());
        return damage;
    }

    private float getDamageMultiplied(float damage) {
        int diff = AutoCrystal.mc.world.getDifficulty().getId();
        return damage * (diff == 0 ? 0.0f : (diff == 2 ? 1.0f : 0.f));
    }

}
