package com.example.projectgestionarmobile.ui.theme.util

import com.example.projectgestionarmobile.ui.theme.model.ModelIdPhone

object SimpleDataManager {
    private var currentModel = ModelIdPhone("", "")

    fun setScreenshotText(text: String?) {
        if (text != null) {
            currentModel.MLID = text
        }
    }

    fun setPhoneNumber(number: String) {
        currentModel.setPhoneNumber(number)
    }

    fun getScreenShot () : String{
        return this.currentModel.MLID
    }

    fun getPhone () : String{
        return this.currentModel.phoneNumber
    }



    override fun toString(): String {
        return "IMG: "+ currentModel.MLID + " PHONE: "+ currentModel.phoneNumber
    }

}