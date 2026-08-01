package com.github.aoudiamoncef.apollo.plugin.config

import com.apollographql.apollo.compiler.MODELS_OPERATION_BASED
import com.apollographql.apollo.compiler.MODELS_OPERATION_BASED_WITH_INTERFACES
import com.apollographql.apollo.compiler.MODELS_RESPONSE_BASED

/**
 * The codegen model to use.
 *
 * The labels come from the Apollo compiler constants so that an upstream rename surfaces here at
 * compile time rather than as a runtime failure.
 *
 * Note: the `compat` model was removed in Apollo 4 and has no replacement.
 */
enum class Codegen(
    val label: String,
) {
    OPERATION(MODELS_OPERATION_BASED),
    RESPONSE(MODELS_RESPONSE_BASED),
    OPERATION_WITH_INTERFACES(MODELS_OPERATION_BASED_WITH_INTERFACES),
}
