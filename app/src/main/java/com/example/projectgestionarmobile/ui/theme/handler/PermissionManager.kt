package com.example.projectgestionarmobile.ui.theme.handler

import android.content.Context
import android.content.SharedPreferences

class PermissionManager(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("Permissions", Context.MODE_PRIVATE)

    fun setPermissionGranted(permission: String) {
        sharedPreferences.edit().putBoolean(permission, true).apply()
    }

}