package w.thx

import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Process.myPid
import android.os.Process.setThreadPriority
import java.io.File
import java.net.Inet4Address
import java.net.Inet6Address


class ThxService : VpnService() {

    override fun onCreate() {
        sThxService = this
        setThreadPriority(myPid(), -20)
        super.onCreate()
    }

    private fun onConnected(c: HashMap<String, Any>, address: ByteArray) {

        runOnMainLoop {
            val conn = nativeFD()
            val builder = Builder()
            val addr4Str: String?
            val addr6Str: String?

            protect(conn)
            if (address[0] != 0.toByte()) {
                try {
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

                } catch (e: Exception) {
                    showToastMsg(e.message)
                    return@runOnMainLoop
                }

            } else {
                showToastMsg("连接被拒绝")
                return@runOnMainLoop
            }

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

            try {
                val tun = builder.establish() ?: throw Exception("tun 错误")
                if (!nativePrep(tun.detachFd())) // 当发生错误 资源在 Native 释放掉
                    throw Exception("Prep 错误")

            } catch (e: Exception) {
                showToastMsg(e.message)
                stopSelf()
                return@runOnMainLoop
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

        private var sTime = 0L

        private var sThxService: ThxService? = null

        @JvmStatic
        private external fun nativeFD(): Int // 连接隧道 文件描述符

        @JvmStatic
        private external fun nativeLoop()

        @JvmStatic
        private external fun nativePrep(tun: Int): Boolean

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
                    startService(Intent(this, ThxService::class.java))
                }
            }

            Thread {
                ThxActivity.setConnecting()
                val file = File(getInstance().dataDir.absolutePath + CONFIG_PATH)
                if (!file.exists()) {
                    showToastMsg("未找到配置文件")
                    ThxActivity.setUnconnected()
                    return@Thread
                }
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
                "${System.currentTimeMillis()}\nbrand:  ${android.os.Build.BRAND}\nproduct:  ${android.os.Build.PRODUCT}\n".encodeToByteArray()

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

            while (true) {
                if (ThxActivity.sState == ThxActivity.CONNECTED) {
                    return
                } else if (ThxActivity.sState == ThxActivity.UNCONNECTED && System.currentTimeMillis() - sTime > 5000) {
                    sTime = System.currentTimeMillis()
                    showToastMsg("正在重新建立连接")
                    connect()
                } else {
                    Thread.sleep(2000)
                }
            }

        }

    }

}


