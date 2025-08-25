/*
 * BreweryX Bukkit-Plugin for an alternate brewing process
 * Copyright (C) 2024-2025 The Brewery Team
 *
 * This file is part of BreweryX.
 *
 * BreweryX is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BreweryX is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BreweryX. If not, see <http://www.gnu.org/licenses/gpl-3.0.html>.
 */

package com.dre.brewery.integration.listeners.movecraft;

import com.dre.brewery.Barrel;
import com.dre.brewery.utility.BoundingBox;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.events.CraftTranslateEvent;
import net.countercraft.movecraft.util.hitboxes.HitBox;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public class TranslationListener implements Listener {
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void translateListener(CraftTranslateEvent event) {
        Vector delta = delta(event);
        if (delta == null)
            return;

        Craft craft = event.getCraft();
        HitBox hitBox = craft.getHitBox();
        for (Barrel barrel : MovecraftUtil.barrelsOnCraft(hitBox, craft.getWorld())) {
            Location location = barrel.getSpigot().getLocation();

            BoundingBox box = barrel.getBounds();
            box.setMin(move(box.getMin(), delta));
            box.setMax(move(box.getMax(), delta));
            barrel.setSpigot( location.add(delta.getX(), delta.getY(), delta.getZ()).getBlock() );
        }
    }

    @Nullable
    private Vector delta(@NotNull CraftTranslateEvent event) {
        if (event.getOldHitBox().isEmpty() || event.getNewHitBox().isEmpty())
            return null;

        MovecraftLocation oldMid = event.getOldHitBox().getMidPoint();
        MovecraftLocation newMid = event.getNewHitBox().getMidPoint();

        int dx = newMid.getX() - oldMid.getX();
        int dy = newMid.getY() - oldMid.getY();
        int dz = newMid.getZ() - oldMid.getZ();

        return new Vector(dx, dy, dz);
    }

    @NotNull
    private BoundingBox.BlockPos move(@NotNull BoundingBox.BlockPos pos, @NotNull Vector vec) {
        return new BoundingBox.BlockPos(
            pos.x() + vec.getBlockX(),
            pos.y() + vec.getBlockY(),
            pos.z() + vec.getBlockZ()
        );
    }
}
