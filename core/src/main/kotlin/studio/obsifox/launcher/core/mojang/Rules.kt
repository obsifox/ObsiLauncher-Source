package studio.obsifox.launcher.core.mojang

import studio.obsifox.launcher.core.util.Platform

/** Evaluation of Mojang's `rules` arrays (libraries and arguments). Last matching rule wins. */
object Rules {
    fun osMatches(o: OsRule?): Boolean {
        if (o == null) return true
        if (o.name != null && o.name != Platform.os.mojang) return false
        if (o.arch != null && o.arch != Platform.arch) return false
        if (o.version != null && !Regex(o.version).containsMatchIn(Platform.osVersion)) return false
        return true
    }

    fun allowed(rules: List<Rule>?, features: Map<String, Boolean> = emptyMap()): Boolean {
        if (rules.isNullOrEmpty()) return true
        var result = false
        for (r in rules) {
            if (!osMatches(r.os)) continue
            val f = r.features
            if (f != null && !f.all { (k, v) -> (features[k] ?: false) == v }) continue
            result = r.action == "allow"
        }
        return result
    }
}

/** Helpers for Maven coordinates ("group:artifact:version[:classifier][@ext]"). */
object Maven {
    fun path(name: String, classifierOverride: String? = null): String {
        val ext = if ('@' in name) name.substringAfter('@') else "jar"
        val parts = name.substringBefore('@').split(':')
        require(parts.size >= 3) { "Bad maven coordinate: $name" }
        val classifier = classifierOverride ?: parts.getOrNull(3)
        val file = parts[1] + "-" + parts[2] + (if (classifier != null) "-$classifier" else "") + ".$ext"
        return parts[0].replace('.', '/') + "/" + parts[1] + "/" + parts[2] + "/" + file
    }

    /** group:artifact[:classifier] - used to de-duplicate libraries between a child version and its parent. */
    fun key(name: String): String {
        val parts = name.substringBefore('@').split(':')
        return parts.getOrElse(0) { "" } + ":" + parts.getOrElse(1) { "" } + (parts.getOrNull(3)?.let { ":$it" } ?: "")
    }
}
