package com.momentoshardcore;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class RouletteItem extends Item {
    /** Lo asigna el cliente al iniciar (así el servidor nunca carga clases de cliente). */
    public static Runnable OPEN_SCREEN;

    public RouletteItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient && OPEN_SCREEN != null) {
            OPEN_SCREEN.run();
        }
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient);
    }
}
