package com.ludosovereign.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Small robot head for bot seats (material-icons-core has no robot, and the extended set is ~10 MB). */
val RobotIcon: ImageVector by lazy {
    ImageVector.Builder("Robot", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            // Antenna
            moveTo(11f, 2f); lineTo(13f, 2f); lineTo(13f, 5f); lineTo(11f, 5f); close()
            // Head with two eye holes and a mouth slot
            moveTo(6f, 5f); lineTo(18f, 5f)
            arcToRelative(3f, 3f, 0f, false, true, 3f, 3f); lineTo(21f, 16f)
            arcToRelative(3f, 3f, 0f, false, true, -3f, 3f); lineTo(6f, 19f)
            arcToRelative(3f, 3f, 0f, false, true, -3f, -3f); lineTo(3f, 8f)
            arcToRelative(3f, 3f, 0f, false, true, 3f, -3f); close()
            moveTo(7f, 9f); lineTo(10f, 9f); lineTo(10f, 12f); lineTo(7f, 12f); close()
            moveTo(14f, 9f); lineTo(17f, 9f); lineTo(17f, 12f); lineTo(14f, 12f); close()
            moveTo(8f, 14.5f); lineTo(16f, 14.5f); lineTo(16f, 16f); lineTo(8f, 16f); close()
            // Ears
            moveTo(1f, 10f); lineTo(2.5f, 10f); lineTo(2.5f, 14f); lineTo(1f, 14f); close()
            moveTo(21.5f, 10f); lineTo(23f, 10f); lineTo(23f, 14f); lineTo(21.5f, 14f); close()
            // Neck/shoulders
            moveTo(7f, 20f); lineTo(17f, 20f); lineTo(18f, 23f); lineTo(6f, 23f); close()
        }
    }.build()
}

/** Trophy cup for Stats & History. */
val TrophyIcon: ImageVector by lazy {
    ImageVector.Builder("Trophy", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            // Cup with side handles
            moveTo(6f, 3f); lineTo(18f, 3f); lineTo(18f, 5f); lineTo(21f, 5f); lineTo(21f, 8f)
            arcToRelative(4f, 4f, 0f, false, true, -3.6f, 4f)
            arcToRelative(6f, 6f, 0f, false, true, -4.4f, 3.9f); lineTo(13f, 18f); lineTo(16f, 18f); lineTo(16f, 21f)
            lineTo(8f, 21f); lineTo(8f, 18f); lineTo(11f, 18f); lineTo(11f, 15.9f)
            arcToRelative(6f, 6f, 0f, false, true, -4.4f, -3.9f)
            arcToRelative(4f, 4f, 0f, false, true, -3.6f, -4f); lineTo(3f, 5f); lineTo(6f, 5f); close()
            moveTo(5f, 7f); lineTo(5f, 8f); arcToRelative(2f, 2f, 0f, false, false, 1.1f, 1.8f); lineTo(6f, 7f); close()
            moveTo(19f, 7f); lineTo(18f, 7f); lineTo(17.9f, 9.8f); arcToRelative(2f, 2f, 0f, false, false, 1.1f, -1.8f); close()
        }
    }.build()
}

/** Two beamed notes for Audio Studio. */
val MusicIcon: ImageVector by lazy {
    ImageVector.Builder("Music", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(9f, 4f); lineTo(21f, 2f); lineTo(21f, 16f)
            arcToRelative(3f, 3f, 0f, true, true, -2f, -2.83f); lineTo(19f, 6.4f); lineTo(11f, 7.7f); lineTo(11f, 18f)
            arcToRelative(3f, 3f, 0f, true, true, -2f, -2.83f); close()
        }
    }.build()
}
