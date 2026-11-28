package es.planidroid.app.domain

internal const val UnassignedCompartment = "Sin asignar"

internal data class FurnitureLayout(
    val original: List<Zone>,
    val zones: List<Zone> = original,
    private val destinations: Map<String, String> = original.associate { it.name to it.name }
) {
    fun replace(old: Zone?, next: Zone): FurnitureLayout {
        val peers = zones.filterNot { it == old }
        require(next.name.isNotBlank() && peers.none { it.name.equals(next.name, true) } &&
            (next.name == old?.name || !next.name.equals(UnassignedCompartment, true))) {
            "Elige un nombre de compartimento distinto."
        }
        require(LayoutGrid.fits(next, peers)) { "El compartimento no cabe en esa posición." }
        return copy(
            zones = if (old == null) zones + next else zones.map { if (it == old) next else it },
            destinations = destinations.mapValues { (_, name) -> if (name == old?.name) next.name else name }
        )
    }

    fun remove(zone: Zone, destination: String): FurnitureLayout {
        val remaining = zones - zone
        require(destination == UnassignedCompartment || remaining.any { it.name == destination })
        return copy(
            zones = remaining,
            destinations = destinations.mapValues { (_, name) -> if (name == zone.name) destination else name }
        )
    }

    fun furniture(current: Furniture): Furniture {
        require(LayoutGrid.zones(current) == original) {
            "La distribución cambió. Vuelve a abrir el editor para actualizarla."
        }
        return current.copy(zones = zones, compartments = zones.map { it.name })
    }

    fun item(value: Item): Item {
        val destination = destinations[value.compartment] ?: return value
        return if (destination == value.compartment) value else value.copy(compartment = destination)
    }
}
