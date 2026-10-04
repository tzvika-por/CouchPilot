package com.myremote.app

import android.app.Application

class RemoteApplication : Application() {
    val remoteSession: RemoteSession by lazy { RemoteSession(this) }
}
