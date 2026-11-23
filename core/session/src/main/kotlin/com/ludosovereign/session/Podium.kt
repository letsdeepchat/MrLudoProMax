package com.ludosovereign.session

/** Podium titles shown in yards, pods and the post-match summary. */
object Podium {
    /** [place] is 1-based: 1st Ultra, 2nd Pro, 3rd Max, everyone after that Aspirant. */
    fun title(place: Int): String = when (place) {
        1 -> "Ultra"
        2 -> "Pro"
        3 -> "Max"
        else -> "Aspirant"
    }
}
