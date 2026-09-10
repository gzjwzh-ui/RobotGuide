package com.robot.guide

import android.app.Application
import com.robot.guide.db.DatabaseHelper

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        // 初始化数据库
        DatabaseHelper.getInstance(this)
    }
}
