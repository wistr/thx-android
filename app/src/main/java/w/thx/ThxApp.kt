package w.thx

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

class ThxApp : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        sInstance = this
    }
}

private var sInstance: ThxApp? = null

fun getInstance(): ThxApp {
    return sInstance!!
}

fun runOnMainLoop(r: Runnable) {
    Handler(Looper.getMainLooper()).post(r)
}

fun showToastMsg(msg: String?) {
    msg ?: return
    runOnMainLoop {
        Toast.makeText(sInstance, msg, Toast.LENGTH_SHORT).show()
    }
}
