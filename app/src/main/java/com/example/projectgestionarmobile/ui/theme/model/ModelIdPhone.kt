package com.example.projectgestionarmobile.ui.theme.model

import java.util.UUID

class ModelIdPhone(var MLID: String, phoneNumber: String) {

    var phoneNumber: String = phoneNumber
        private set

    override fun toString(): String {
        return "ModelIdPhone(MLID='$MLID', phoneNumber='$phoneNumber')"
    }

    fun setPhoneNumber(number: String) {
        this.phoneNumber = number
    }
}