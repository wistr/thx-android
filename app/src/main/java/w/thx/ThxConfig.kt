package w.thx

import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader

const val FILENAME = "thx.x"

const val APP = "\"app\""

const val DNS = "\"dns\""

const val USER = "\"user\""

const val VER = "\"ver\""

const val PORT = "\"port\""

const val ADDR = "\"addr\""

const val PATH = "\"path\""

fun getCertFilePath(): String {
    return getInstance().dataDir.absolutePath + File.separator + "CERT"
}

// 未处理异常
fun import(uri: Uri) {

    val app = getInstance()

    val fi = app.contentResolver.openInputStream(uri)!!
    val size = 1024 * 512
    val buf = ByteArray(size)
    val len = fi.read(buf)
    fi.close()

    if (len >= size) {
        showToastMsg("文件太大")
        return
    }

    val bytes = buf.copyOfRange(0, len)

    val certBytes = getPEM(bytes)

    if (certBytes == null) {
        showToastMsg("未找到证书")
        return
    }

    FileOutputStream(File(getCertFilePath())).run {
        write(certBytes)
        flush()
        close()
    }

    FileOutputStream(File(app.dataDir.absolutePath + File.separator + FILENAME)).run {
        write(bytes)
        flush()
        close()
    }

    showToastMsg("导入成功")

}

private fun getPEM(byteArray: ByteArray): ByteArray? {

    var end = false
    var begin = false

    val lines = String(byteArray).split("\n")
    val byteArrayOutputStream = ByteArrayOutputStream()

    for (l in lines) {

        if (!begin && l.contains("-----BEGIN CERTIFICATE-----"))
            begin = true
        else if (l.contains("-----END CERTIFICATE-----"))
            end = true

        if (begin) {
            byteArrayOutputStream.write(l.encodeToByteArray())
            byteArrayOutputStream.write('\n'.code)
        }

        if (end)
            return byteArrayOutputStream.toByteArray()
    }

    return null
}

fun load(file: File): HashMap<String, Any> {

    val fr = FileReader(file)
    val lines = fr.readLines()
    val dns = ArrayList<String>()
    val app = ArrayList<String>()
    val map = HashMap<String, Any>()

    map[DNS] = dns
    map[APP] = app

    for (l in lines) {
        val user = valOf(USER, l)
        if (!user.isNullOrEmpty()) {
            map[USER] = user
            continue
        }

        val ver = valOf(VER, l)
        if (!ver.isNullOrEmpty()) {
            map[VER] = ver
            continue
        }

        val port = valOf(PORT, l)
        if (!port.isNullOrEmpty()) {
            map[PORT] = port
            continue
        }

        val address = valOf(ADDR, l)
        if (!address.isNullOrEmpty()) {
            map[ADDR] = address
            continue
        }

        val path = valOf(PATH, l)
        if (!path.isNullOrEmpty()) {
            map[PATH] = path
            continue
        }

        val d = valOf(DNS, l)
        if (!d.isNullOrEmpty()) {
            dns.add(d)
            continue
        }

        val a = valOf(APP, l)
        if (!a.isNullOrEmpty()) {
            app.add(a)
            continue
        }

    }

    return map

}

fun check(c: HashMap<String, Any>): Boolean {

    try {

        val path = c[PATH] as String? ?: throw Exception("未填写路径")
        if (path.length > 64)
            throw Exception("路径超长")


        c[ADDR] as String? ?: throw Exception("未填写地址")

        val verStr = c[VER] as String? ?: throw Exception("未填写IP版本")
        if (verStr[0] != '4' && verStr[0] != '6')
            throw Exception("IP版本错误")

        val userStr = c[USER] as String? ?: throw Exception("未填写用户")
        if (userStr.length > 32)
            throw Exception("用户超长")


        val dns = c[DNS] as ArrayList<*>
        if (dns.isEmpty())
            throw Exception("未填写 DNS")

        val portStr = c[PORT] as String? ?: throw Exception("未填写端口")
        try {
            val p = portStr.toInt()
            if (p <= 0 || p > 65535)
                throw NumberFormatException()

        } catch (e: NumberFormatException) {
            throw Exception("错误端口 $portStr")
        }

    } catch (e: Exception) {
        showToastMsg(e.message)
        return false
    }

    return true
}


private fun valOf(k: String, line: String): String? {
    if (!line.contains(k)) return null
    val ss = line.split("\":\"")
    if (ss.size != 2) return null
    val index = ss[1].indexOf("\"")
    if (index == -1) return null
    return ss[1].substring(0, index)
}
