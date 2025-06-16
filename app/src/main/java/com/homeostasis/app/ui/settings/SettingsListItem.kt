package com.homeostasis.app.ui.settings

// Sealed class to represent different types of items in the settings list
sealed class SettingsListItem {
    data class Header(val title: String) : SettingsListItem()
    data class Setting(val name: String) : SettingsListItem()
    }