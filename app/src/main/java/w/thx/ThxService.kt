package w.thx

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import java.io.File
import java.net.Inet4Address
import java.net.Inet6Address

class ThxService : VpnService() {

    override fun onCreate() {
        sThxService = this
        val channelId = "默认通知"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                channelId,
                channelId,
                NotificationManager.IMPORTANCE_NONE
            )
        )

        startForeground(
            2,
            Notification.Builder(this, channelId).setSmallIcon(R.mipmap.ic_launcher_foreground)
                .setContentText("正在运行")
                .build()
        )
        super.onCreate()
    }

    override fun onDestroy() {
        stopForeground(true)
        super.onDestroy()
    }

    private fun onConnected(c: HashMap<String, Any>, address: ByteArray) {

        runOnMainLoop {
            val addr4Str: String?
            val addr6Str: String?

            try {
                val conn = nativeConn()
                val builder = Builder()

                if (address[0] == 0.toByte())
                    throw Exception("连接被拒绝")

                val ipv4AddressBytes = ByteArray(4)
                for (i in 0 until 4)
                    ipv4AddressBytes[i] = address[i]

                val inet4Address = Inet4Address.getByAddress(ipv4AddressBytes)
                addr4Str = inet4Address.hostAddress
                builder.addAddress(inet4Address, 32)

                val ipv6AddressBytes = ByteArray(16)
                for (i in 0 until 16)
                    ipv6AddressBytes[i] = address[i + 4]

                val inet6Address = Inet6Address.getByAddress(ipv6AddressBytes)
                addr6Str = inet6Address.hostAddress
                builder.addAddress(inet6Address, 128)

                protect(conn)
                builder.addRoute("0.0.0.0", 0)
                builder.addRoute("::", 0)

                (c[APP] as ArrayList<*>).forEach {
                    if (it is String) {
                        try {
                            builder.addAllowedApplication(it)
                        } catch (_: PackageManager.NameNotFoundException) {
                        }
                    }
                }

                (c[DNS] as ArrayList<*>).forEach {
                    if (it is String) {
                        try {
                            builder.addDnsServer(it)
                        } catch (e: Exception) {
                            showToastMsg(e.message)
                        }
                    }
                }

                val tun = builder.establish() ?: throw Exception("tun 错误")
                if (!nativePrep(tun.detachFd()))
                    throw Exception("Prep 错误")

            } catch (e: Exception) {
                nativeCloseConn()
                return@runOnMainLoop connFailed(e.message)
            }

            ThxActivity.setConnected(addr4Str, addr6Str)
            ThxActivity.sAuto = true

            Thread {
                nativeLoop()
                ThxActivity.setUnconnected()
                restartThxService()

            }.start()

        }

    }

    companion object {

        private var sThxService: ThxService? = null

        @JvmStatic
        private external fun nativeConn(): Int // 连接隧道 文件描述符

        @JvmStatic
        private external fun nativeLoop()

        @JvmStatic
        private external fun nativePrep(tun: Int): Boolean

        @JvmStatic
        private external fun nativeCloseConn()

        @JvmStatic
        private external fun nativeAESInit(
            iv: ByteArray,
            ivSize: Int,
            key: ByteArray,
            keySize: Int
        )

        @JvmStatic
        private external fun sslConn(
            ver: Int,
            host: ByteArray,
            path: ByteArray,
            port: Int,
            user: ByteArray,
            info: ByteArray,
        ): ByteArray?

        @JvmStatic
        private external fun tcpConn(
            ver: Int,
            addr: ByteArray,
            port: Int,
            user: ByteArray,
            info: ByteArray,
        ): ByteArray?

        private fun connFailed(msg: String?) {
            showToastMsg(msg)
            ThxActivity.setUnconnected()
        }

        fun connect() {
            if (sThxService == null) {
                getInstance().run {
                    startForegroundService(Intent(this, ThxService::class.java))
                }
            }
            Thread {
                ThxActivity.setConnecting()
                val file = File(getInstance().dataDir.absolutePath + File.separator + FILENAME)
                if (!file.exists())
                    return@Thread connFailed("未找到配置文件")

                connect(load(file))

            }.start()

        }

        private fun connect(c: HashMap<String, Any>) {
            if (!check(c))
                return connFailed(null)

            val iv = (c[IV] as String).encodeToByteArray()
            val key = (c[KEY] as String).encodeToByteArray()
            nativeAESInit(iv, iv.size, key, key.size)

            val info =
                "brand:  ${android.os.Build.BRAND}\nproduct:  ${android.os.Build.PRODUCT}\n".encodeToByteArray()

            val tls = c[TLS] as String?
            val address: ByteArray? = if (tls != null && tls[0] == '1')
                sslConn(
                    (c[VER] as String).toInt(),
                    (c[ADDR] as String).encodeToByteArray(),
                    (c[PATH] as String).encodeToByteArray(),
                    (c[PORT] as String).toInt(),
                    (c[USER] as String).encodeToByteArray(),
                    info
                )
            else
                tcpConn(
                    (c[VER] as String).toInt(),
                    (c[ADDR] as String).encodeToByteArray(),
                    (c[PORT] as String).toInt(),
                    (c[USER] as String).encodeToByteArray(),
                    info
                )

            if (address == null)
                connFailed("连接失败")
            else
                sThxService!!.onConnected(c, address)

        }

        private fun restartThxService() {

            var time = 0L
            while (true) {
                if (ThxActivity.sState == ThxActivity.CONNECTED) {
                    return
                } else if (ThxActivity.sState == ThxActivity.UNCONNECTED && System.currentTimeMillis() - time > 5000) {
                    time = System.currentTimeMillis()
                    showToastMsg("正在重新建立连接")
                    connect()
                } else {
                    Thread.sleep(2000)
                }
            }

        }

    }

}


