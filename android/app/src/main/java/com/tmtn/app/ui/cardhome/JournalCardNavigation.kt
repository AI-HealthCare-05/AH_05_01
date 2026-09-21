package com.tmtn.app.ui.cardhome

/** Explicit journal/record CTA; ordinary bottom-tab navigation keeps the previous home screen. */
internal fun journalCardDestination(rest: Boolean, challengeState: String?, challengeId: String?): CardHomeStep = when {
    rest || challengeState in setOf("COMPLETED", "SKIPPED") -> CardHomeStep.HOME
    challengeId != null -> CardHomeStep.REVEALED
    else -> CardHomeStep.DECK_PICK
}
