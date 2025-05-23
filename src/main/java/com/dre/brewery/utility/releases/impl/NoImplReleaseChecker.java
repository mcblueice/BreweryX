/*
 * BreweryX Bukkit-Plugin for an alternate brewing process
 * Copyright (C) 2024 The Brewery Team
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

package com.dre.brewery.utility.releases.impl;

import com.dre.brewery.utility.releases.ReleaseChecker;
import org.bukkit.command.CommandSender;

import java.util.concurrent.CompletableFuture;

public class NoImplReleaseChecker extends ReleaseChecker {

    @Override
    public CompletableFuture<String> resolveLatest() {
        this.resolvedLatestVersion = CONST_UNRESOLVED;
        return CompletableFuture.completedFuture(CONST_UNRESOLVED);
    }

    @Override
    public boolean isUpdateAvailable() {
        return false;
    }

    @Override
    public CompletableFuture<Boolean> checkForUpdate() {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public void notify(CommandSender receiver) {
        // No implementation
    }

    @Override
    public String getDownloadURL() {
        return "";
    }
}
