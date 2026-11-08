package com.example.titles.api;

import org.bukkit.entity.Player;

/** Main-thread-only service owning the floating TextDisplay passengers and PDC title state. */
public interface TitleDisplayService {
    /** Applies {@code title} if its priority is >= the active priority; otherwise ignores it. */
    void setPlayerTitle(Player player, AwardedTitle title);

    /** The player's active title as stored in PDC (text, priority and reason; no comment). */
    java.util.Optional<AwardedTitle> currentTitle(Player player);

    /** Removes the title from PDC and despawns the display (admin action, ignores priority). */
    void clearPlayerTitle(Player player);

    /** Ensures exactly one display exists and is mounted, rebuilt from PDC state; no-op if no title. */
    void refreshDisplay(Player player);

    void updateSneakState(Player player, boolean isSneaking);

    /** Despawns and unregisters the display entity but KEEPS the PDC title (so it can be restored). */
    void removeDisplay(Player player);

    /** Despawns every tracked display (plugin disable). */
    void cleanupAll();
}
