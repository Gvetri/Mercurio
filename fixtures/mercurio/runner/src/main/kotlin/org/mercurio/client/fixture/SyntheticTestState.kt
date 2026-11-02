package org.mercurio.client.fixture

/** Mutable synthetic state that is scoped to one fixture test and never leaves the process. */
class SyntheticTestState internal constructor(
    private val resetFunction: (MutableMap<String, String>) -> Unit = { it.clear() },
) {
    private val values = linkedMapOf<String, String>()

    fun put(
        key: String,
        value: String,
    ) {
        require(key.matches(Regex("[a-z][a-z0-9-]*"))) {
            "Synthetic state keys must use lowercase kebab-case."
        }
        values[key] = value
    }

    fun get(key: String): String? = values[key]

    fun snapshot(): Map<String, String> = values.toMap()

    internal fun reset() = resetFunction(values)
}
