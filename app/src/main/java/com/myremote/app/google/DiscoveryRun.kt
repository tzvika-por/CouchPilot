package com.myremote.app.google

/** Tokens describe a service appearance in one run, not just a reusable service name. */
internal class DiscoveryRun {
    data class Token(val generation: Int, val appearance: Int)
    private var generation = 0
    private var appearance = 0
    private var active = false
    private val found = mutableMapOf<String, Token>()
    @Synchronized fun start(): Int { active = true; found.clear(); return ++generation }
    @Synchronized fun stop() { active = false; found.clear(); generation++ }
    @Synchronized fun current(run: Int): Boolean = active && run == generation
    @Synchronized fun found(run: Int, name: String): Token? =
        if (current(run)) Token(run, ++appearance).also { found[name] = it } else null
    @Synchronized fun lost(run: Int, name: String) { if (current(run)) found.remove(name) }
    @Synchronized fun accepts(name: String, token: Token): Boolean = current(token.generation) && found[name] == token
}
