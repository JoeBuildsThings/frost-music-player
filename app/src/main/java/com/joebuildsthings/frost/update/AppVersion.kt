package com.joebuildsthings.frost.update

/**
 * Semantic version from a release tag or versionName: "v0.7.0", "0.7.1", "0.8.0-beta.2",
 * "0.7.0-dev.19". A pre-release sorts below its release, numeric parts compare as numbers.
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        compareValues(major, other.major).let { if (it != 0) return it }
        compareValues(minor, other.minor).let { if (it != 0) return it }
        compareValues(patch, other.patch).let { if (it != 0) return it }
        return comparePre(preRelease, other.preRelease)
    }

    override fun toString(): String = "$major.$minor.$patch" + (preRelease?.let { "-$it" } ?: "")

    companion object {
        private val PATTERN = Regex("""^[vV]?(\d+)\.(\d+)(?:\.(\d+))?(?:-([0-9A-Za-z.-]+))?(?:\+\S*)?$""")

        fun parse(raw: String?): AppVersion? {
            val m = PATTERN.matchEntire(raw?.trim() ?: return null) ?: return null
            val (major, minor, patch, pre) = m.destructured
            return AppVersion(
                major.toIntOrNull() ?: return null,
                minor.toIntOrNull() ?: return null,
                patch.ifEmpty { "0" }.toIntOrNull() ?: return null,
                pre.ifEmpty { null }
            )
        }

        private fun comparePre(a: String?, b: String?): Int {
            if (a == b) return 0
            if (a == null) return 1
            if (b == null) return -1
            val pa = a.split('.')
            val pb = b.split('.')
            for (i in 0 until minOf(pa.size, pb.size)) {
                val na = pa[i].toLongOrNull()
                val nb = pb[i].toLongOrNull()
                val c = when {
                    na != null && nb != null -> na.compareTo(nb)
                    na != null -> -1
                    nb != null -> 1
                    else -> pa[i].compareTo(pb[i])
                }
                if (c != 0) return c
            }
            return pa.size.compareTo(pb.size)
        }
    }
}
