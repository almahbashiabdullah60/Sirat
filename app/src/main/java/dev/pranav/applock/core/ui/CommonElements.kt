package dev.pranav.applock.core.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape

val shapes = mutableListOf(
    CircleShape,
    RoundedCornerShape(20),
    RectangleShape,
    RoundedCornerShape(10),
    CircleShape,
    RoundedCornerShape(15),
    RectangleShape,
    CircleShape,
    RoundedCornerShape(25),
    CircleShape
).apply { shuffle() }
