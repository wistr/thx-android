package w.thx

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.View
import android.widget.TextView

class ThxActivity : Activity() {

    private var mIpa4: TextView? = null

    private var mIpa6: TextView? = null

    private var mState: TextView? = null

    private var mConnBtn: TextView? = null

    private var mAkari: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {

        setContentView(R.layout.main)
        mIpa4 = findViewById(R.id.ipv4)
        mIpa6 = findViewById(R.id.ipv6)
        mState = findViewById(R.id.state)
        mConnBtn = findViewById<TextView?>(R.id.connect).apply {
            setOnClickListener {
                if (!sAuto)
                    connect()
            }
        }

        mAkari = findViewById<View?>(R.id.ic).apply {
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

            if (addr4 != null)
                mIpa4?.text = addr4

            if (addr6 != null)
                mIpa6?.text = addr6

            mState?.run {
                text = "连接成功"
                setTextColor(0xFF78B4B4.toInt())
            }

            mConnBtn?.visibility = View.INVISIBLE
        }
    }

    private fun unconnected() {
        runOnUiThread {
            mIpa4?.text = ""
            mIpa6?.text = ""
            mConnBtn?.visibility = View.VISIBLE
            mState?.run {
                text = "未连接"
                setTextColor(0xFFB47878.toInt())
            }
        }
    }

    private fun connecting() {
        runOnUiThread {
            mState?.text = "正在连接"
            mConnBtn?.visibility = View.INVISIBLE
        }
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