package eu.komars.forgescanner.model

/** Advisory feedback only; it never confirms an inventory count. */
enum class ScrollGuidance(val label: String) {
    SCROLL("Przewijaj do końca listy"),
    SLOW("Wolniej — mniejsze przesunięcia"),
    PAUSE("Zatrzymaj na chwilę — trudno śledzić ruch")
}
