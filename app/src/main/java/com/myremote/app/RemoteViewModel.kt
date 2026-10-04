package com.myremote.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel

/** UI recreation must never dispose the service-owned device session. */
class RemoteViewModel(application: Application) : AndroidViewModel(application) {
    val session = (application as RemoteApplication).remoteSession
}
