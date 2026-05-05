package com.snapsell.nativecamera.data

import androidx.compose.ui.graphics.Color

enum class AiFilter(val label: String, val prompt: String, val color: Color, val requiresCloud: Boolean = false) {
    ENHANCE("Enhance", "Enhance this product photo for an online store. Improve lighting, clarity, and colors while keeping it natural.", Color(0xFF60A5FA)),
    WHITE_BG("White BG", "Remove the background and replace it with a clean, professional studio white background. Keep the product (clothing/accessory) perfectly intact.", Color(0xFF34D399)),
    LIFESTYLE("Lifestyle", "Place this item in a professional lifestyle setting suitable for an online fashion store. Ensure the lighting matches.", Color(0xFFC084FC), requiresCloud = true)
}