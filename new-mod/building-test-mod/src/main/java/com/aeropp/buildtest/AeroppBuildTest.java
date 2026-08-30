package com.aeropp.buildtest;

import net.neoforged.fml.common.Mod;

/**
 * Minimal entry point for the data-driven worldgen experiment.
 * Worldgen content is intentionally kept in data resources so it can be
 * replaced by a datapack or a future scenario service without changing this class.
 */
@Mod(AeroppBuildTest.MOD_ID)
public final class AeroppBuildTest {
    public static final String MOD_ID = "aeropp_buildtest";

    public AeroppBuildTest() {
    }
}
