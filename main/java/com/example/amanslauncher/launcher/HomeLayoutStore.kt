package com.example.amanslauncher.launcher

import android.content.Context

/** A cell on a workspace page grid (0-based page / slot within [HOME_PAGE_APP_CAPACITY]). */
data class HomePlacement(
    val pageIndex: Int,
    val slotIndex: Int,
) {
    init {
        require(pageIndex >= 0)
        require(slotIndex in 0 until HOME_PAGE_APP_CAPACITY)
    }
}

/** Persists home visibility and explicit workspace grid placements. */
class HomeLayoutStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hiddenFromHomeKeys(): Set<String> =
        prefs.getStringSet(KEY_HIDDEN_FROM_HOME, emptySet())?.toSet().orEmpty()

    fun placements(): Map<String, HomePlacement> {
        val raw = prefs.getStringSet(KEY_PLACEMENTS, emptySet()).orEmpty()
        val result = LinkedHashMap<String, HomePlacement>()
        for (entry in raw) {
            parsePlacement(entry)?.let { (key, placement) ->
                if (!key.startsWith("$SELF_LAUNCHER_PACKAGE/")) {
                    result[key] = placement
                }
            }
        }
        return result
    }

    fun hideFromHome(app: LauncherApp) {
        val key = app.componentKey
        val nextHidden = HashSet(hiddenFromHomeKeys() + key)
        val nextPlacements = placements().filterKeys { it != key }
        prefs.edit()
            .putStringSet(KEY_HIDDEN_FROM_HOME, nextHidden)
            .putStringSet(KEY_PLACEMENTS, encodePlacements(nextPlacements))
            .apply()
    }

    /**
     * Places [app] on the workspace at [placement], un-hiding it if needed.
     * Rejects duplicates (already placed or still visible without an explicit hole).
     */
    fun placeOnHome(app: LauncherApp, placement: HomePlacement): Boolean {
        if (app.packageName == SELF_LAUNCHER_PACKAGE) return false
        val key = app.componentKey
        val current = placements().toMutableMap()
        if (current.containsKey(key)) return false

        // Occupied cell?
        if (current.values.any {
                it.pageIndex == placement.pageIndex && it.slotIndex == placement.slotIndex
            }
        ) {
            return false
        }

        current[key] = placement
        val nextHidden = HashSet(hiddenFromHomeKeys()).apply { remove(key) }
        prefs.edit()
            .putStringSet(KEY_HIDDEN_FROM_HOME, nextHidden)
            .putStringSet(KEY_PLACEMENTS, encodePlacements(current))
            .apply()
        return true
    }

    /**
     * Seeds or extends placements so every visible workspace app owns a cell.
     * Empty cells (holes) only appear after [hideFromHome], and are real drop targets.
     */
    fun ensureDefaultPlacements(
        visibleWorkspaceKeys: List<String>,
    ) {
        val filtered = visibleWorkspaceKeys.filterNot {
            it.startsWith("$SELF_LAUNCHER_PACKAGE/")
        }
        if (filtered.isEmpty()) return

        val current = placements().toMutableMap()
        if (current.isEmpty()) {
            filtered.forEachIndexed { index, key ->
                current[key] = HomePlacement(
                    pageIndex = index / HOME_PAGE_APP_CAPACITY,
                    slotIndex = index % HOME_PAGE_APP_CAPACITY,
                )
            }
            prefs.edit()
                .putStringSet(KEY_PLACEMENTS, encodePlacements(current))
                .apply()
            return
        }

        val occupied = current.values.map { it.pageIndex * HOME_PAGE_APP_CAPACITY + it.slotIndex }
            .toMutableSet()
        var changed = false
        for (key in filtered) {
            if (key in current) continue
            var cursor = 0
            while (cursor in occupied) cursor++
            current[key] = HomePlacement(
                pageIndex = cursor / HOME_PAGE_APP_CAPACITY,
                slotIndex = cursor % HOME_PAGE_APP_CAPACITY,
            )
            occupied += cursor
            changed = true
        }
        if (changed) {
            prefs.edit()
                .putStringSet(KEY_PLACEMENTS, encodePlacements(current))
                .apply()
        }
    }

    fun scrubSelfLauncherEntries() {
        val editor = prefs.edit()
        var changed = false
        for (key in prefs.all.keys.toList()) {
            val value = prefs.all[key]?.toString().orEmpty()
            if (key.contains(SELF_LAUNCHER_PACKAGE) || value.contains(SELF_LAUNCHER_PACKAGE)) {
                // Keep our string-set preference keys; prune values below.
                if (key != KEY_HIDDEN_FROM_HOME && key != KEY_PLACEMENTS) {
                    editor.remove(key)
                    changed = true
                }
            }
        }
        val hidden = hiddenFromHomeKeys().filterNot {
            it.startsWith("$SELF_LAUNCHER_PACKAGE/")
        }.toSet()
        if (hidden != hiddenFromHomeKeys()) {
            editor.putStringSet(KEY_HIDDEN_FROM_HOME, hidden)
            changed = true
        }
        val cleanedPlacements = placements() // already filters self-launcher
        val rawPlacements = prefs.getStringSet(KEY_PLACEMENTS, emptySet()).orEmpty()
        val encoded = encodePlacements(cleanedPlacements)
        if (encoded != rawPlacements) {
            editor.putStringSet(KEY_PLACEMENTS, encoded)
            changed = true
        }
        if (changed) {
            editor.apply()
        }
    }

    /**
     * Drops placements for packages that no longer exist, and drops keys still in
     * [hiddenFromHomeKeys] so hidden apps are not double-counted.
     */
    fun reconcilePlacements(existingKeys: Set<String>) {
        val hidden = hiddenFromHomeKeys()
        val current = placements()
        val next = current.filter { (key, _) ->
            key in existingKeys && key !in hidden
        }
        if (next.size != current.size || next != current) {
            prefs.edit()
                .putStringSet(KEY_PLACEMENTS, encodePlacements(next))
                .apply()
        }
    }

    companion object {
        const val PREFS_NAME = "launcher_home_layout"
        private const val KEY_HIDDEN_FROM_HOME = "hidden_from_home"
        private const val KEY_PLACEMENTS = "workspace_placements"

        private fun encodePlacements(map: Map<String, HomePlacement>): Set<String> =
            map.map { (key, p) -> "$key|${p.pageIndex}|${p.slotIndex}" }.toSet()

        private fun parsePlacement(raw: String): Pair<String, HomePlacement>? {
            val parts = raw.split('|')
            if (parts.size != 3) return null
            val page = parts[1].toIntOrNull() ?: return null
            val slot = parts[2].toIntOrNull() ?: return null
            if (page < 0 || slot !in 0 until HOME_PAGE_APP_CAPACITY) return null
            val key = parts[0]
            if (key.isBlank() || !key.contains('/')) return null
            return key to HomePlacement(page, slot)
        }
    }
}

val LauncherApp.componentKey: String
    get() = "$packageName/$activityName"
