package eu.komars.forgescanner.model

internal fun TopCounters.forField(field: ResourceField, screen: GameScreenKind, width: Int, height: Int, pixels: IntArray): PixelRegion? {
    val icon = when (field) {
        ResourceField.GEMS -> TopCounters.Icon.PINK
        ResourceField.COINS -> TopCounters.Icon.CROWN
        ResourceField.SHELLS -> TopCounters.Icon.SHELL
        ResourceField.RED_VIALS -> TopCounters.Icon.RED
        ResourceField.GREEN_VIALS -> TopCounters.Icon.GREEN
        else -> return null
    }
    return forCounter(icon, screen, width, height, pixels)
}
