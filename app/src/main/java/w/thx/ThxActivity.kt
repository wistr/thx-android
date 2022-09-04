package w.thx

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class ThxActivity : Activity() {

    private var mHint: TextView? = null
    private var mIpa4: TextView? = null
    private var mIpa6: TextView? = null
    private var mConnBtn: Button? = null
    private var mDownBtn: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {

        setContentView(R.layout.home)
        mHint = findViewById(R.id.hint)
        mIpa4 = findViewById(R.id.ipv4)
        mIpa6 = findViewById(R.id.ipv6)

        mConnBtn = findViewById<Button?>(R.id.button_switch).apply {
            setOnClickListener {
                if (sAuto || sState != UNCONNECTED) {
                    showToastMsg("Unable to click")
                    return@setOnClickListener
                }
                connect()
            }
        }

        mDownBtn = findViewById<Button?>(R.id.button_down).apply {
            setOnClickListener {
                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.type = "*/*"
                startActivityForResult(Intent.createChooser(intent, "选择配置文件"), IMPORT_FILE)
            }
        }

        sActivity = this
        when (sState) {
            UNCONNECTED -> setUnconnected()
            CONNECTING -> setConnecting()
            CONNECTED -> setConnected(sAddress4, sAddress6)
        }

        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        sActivity = null
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        when (requestCode) {
            VPN_PERM -> {
                if (resultCode != RESULT_OK) {
                    showToastMsg("权限不足")
                    setUnconnected()
                    return
                }
                ThxService.connect()
            }
            IMPORT_FILE -> {
                val uri = data?.data
                if (resultCode != RESULT_OK || uri == null)
                    return

                import(uri)
            }
        }

    }

    private fun connect() {

        val intent = VpnService.prepare(this)
        if (intent == null)
            ThxService.connect()
        else
            startActivityForResult(intent, VPN_PERM)

    }

    private fun connected(addr4: String?, addr6: String?) {
        runOnUiThread {

            mConnBtn?.background = getDrawable(R.drawable.ic_on)
            mHint?.text = getString(R.string.connected)

            if (addr4 != null)
                mIpa4?.text = addr4

            if (addr6 != null)
                mIpa6?.text = addr6

        }
    }

    private fun unconnected() {
        runOnUiThread {
            mConnBtn?.background = getDrawable(R.drawable.ic_off)
            mHint?.text = getString(R.string.tap_to_connect)
        }
    }

    private fun connecting() {
        runOnUiThread { mHint?.text = "" }
    }

    companion object {

        const val CONNECTED = 0

        const val CONNECTING = 2

        const val UNCONNECTED = 4

        private const val VPN_PERM = 1

        private const val IMPORT_FILE = 2

        private var sAddress4: String? = null

        private var sAddress6: String? = null

        @SuppressLint("StaticFieldLeak")
        private var sActivity: ThxActivity? = null

        @Volatile
        var sAuto = false

        @Volatile
        var sState = UNCONNECTED

        init {
            System.loadLibrary("thx")
        }

        fun setUnconnected() {
            sState = UNCONNECTED
            sActivity?.unconnected()
        }

        fun setConnected(addr4: String?, addr6: String?) {
            sState = CONNECTED
            sAddress4 = addr4
            sAddress6 = addr6
            sActivity?.connected(addr4, addr6)
        }

        fun setConnecting() {
            sState = CONNECTING
            sActivity?.connecting()
        }

    }
}