package com.github.arrivedbog593.economycore.platform;

/** How the mod's windows are drawn: like vanilla, dark, or whichever matches the resource packs. */
public enum UiTheme {

    /** Dark when the resource packs in use darken vanilla's own windows, light otherwise. */
    AUTO,

    /** Vanilla's light gray. */
    LIGHT,

    /** Dark, for players on a dark-mode resource pack or client. */
    DARK
}
