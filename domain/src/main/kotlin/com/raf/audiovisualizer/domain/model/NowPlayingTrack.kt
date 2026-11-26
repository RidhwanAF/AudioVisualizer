package com.raf.audiovisualizer.domain.model

/**
 * Active media track metadata and ambient color palette extracted from the current album artwork.
 */
data class NowPlayingTrack(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val packageName: String = "",
    val isPlaying: Boolean = false,
    val artworkBytes: ByteArray? = null,
    val primaryColorArgb: Int = DEFAULT_PRIMARY_ARGB,
    val secondaryColorArgb: Int = DEFAULT_SECONDARY_ARGB,
    val ambientSurfaceArgb: Int = DEFAULT_AMBIENT_ARGB,
) {
    val hasTrack: Boolean get() = title.isNotBlank()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NowPlayingTrack) return false
        return title == other.title &&
            artist == other.artist &&
            album == other.album &&
            packageName == other.packageName &&
            isPlaying == other.isPlaying &&
            artworkBytes.contentEquals(other.artworkBytes) &&
            primaryColorArgb == other.primaryColorArgb &&
            secondaryColorArgb == other.secondaryColorArgb &&
            ambientSurfaceArgb == other.ambientSurfaceArgb
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + packageName.hashCode()
        result = 31 * result + isPlaying.hashCode()
        result = 31 * result + (artworkBytes?.contentHashCode() ?: 0)
        result = 31 * result + primaryColorArgb
        result = 31 * result + secondaryColorArgb
        result = 31 * result + ambientSurfaceArgb
        return result
    }

    companion object {
        const val DEFAULT_PRIMARY_ARGB: Int = 0xFF7C4DFF.toInt()
        const val DEFAULT_SECONDARY_ARGB: Int = 0xFF00E5FF.toInt()
        const val DEFAULT_AMBIENT_ARGB: Int = 0xFF141124.toInt()

        val Empty: NowPlayingTrack = NowPlayingTrack()
    }
}
