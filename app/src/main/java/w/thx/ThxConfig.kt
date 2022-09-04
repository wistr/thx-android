package w.thx

import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader

const val CONFIG_PATH = "/thx.x"

const val APP = "\"app\""

const val DNS = "\"dns\""

const val USER = "\"user\""

const val VER = "\"ver\""

const val PORT = "\"port\""

const val TLS = "\"tls\""

const val ADDR = "\"addr\""

const val PATH = "\"path\""

const val IV = "\"iv\""

const val KEY = "\"key\""


// 未处理异常
fun import(uri: Uri) {

    val context = getInstance()
    val file = File(context.dataDir.absolutePath + CONFIG_PATH)

    if (file.exists()) file.delete()

    if (!file.createNewFile()) {
        showToastMsg("文件创建失败")
        return
    }

    val max = 1024 * 1024 * 2
    var bytes = 0
    val fi = context.contentResolver.openInputStream(uri)!!
    val fo = FileOutputStream(file)
    val buf = ByteArray(2048)

    do {
        val len = fi.read(buf)
        if (len > 0) fo.write(buf, 0, len)
        bytes += len
    } while (len != -1 && bytes < max)


    fi.close()
    fo.flush()
    fo.close()

    if (bytes >= max)
        showToastMsg("文件太大")
    else
        showToastMsg("导入成功")

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
        if (user != null && user.isNotEmpty()) {
            map[USER] = user
            continue
        }

        val ver = valOf(VER, l)
        if (ver != null && ver.isNotEmpty()) {
            map[VER] = ver
            continue
        }

        val port = valOf(PORT, l)
        if (port != null && port.isNotEmpty()) {
            map[PORT] = port
            continue
        }

        val tls = valOf(TLS, l)
        if (tls != null && tls.isNotEmpty()) {
            map[TLS] = tls
            continue
        }

        val address = valOf(ADDR, l)
        if (address != null && address.isNotEmpty()) {
            map[ADDR] = address
            continue
        }

        val path = valOf(PATH, l)
        if (path != null && path.isNotEmpty()) {
            map[PATH] = path
            continue
        }

        val iv = valOf(IV, l)
        if (iv != null && iv.isNotEmpty()) {
            map[IV] = iv
            continue
        }

        val key = valOf(KEY, l)
        if (key != null && key.isNotEmpty()) {
            map[KEY] = key
            continue
        }

        val d = valOf(DNS, l)
        if (d != null && d.isNotEmpty()) {
            dns.add(d)
            continue
        }

        val a = valOf(APP, l)
        if (a != null && a.isNotEmpty()) {
            app.add(a)
            continue
        }

    }

    return map
}

fun check(c: HashMap<String, Any>): Boolean {

    val tlsStr = c[TLS] as String?
    val isSSL: Boolean = tlsStr != null && tlsStr[0] == '1'

    try {

        if (isSSL) {
            val path = c[PATH] as String? ?: throw Exception("未填写路径")
            if (path.length > 64)
                throw Exception("路径超长")
        }

        c[ADDR] as String? ?: throw Exception("未填写地址")

        val verStr = c[VER] as String? ?: throw Exception("未填写IP版本")
        if (verStr[0] != '4' && verStr[0] != '6')
            throw Exception("IP版本错误")

        val userStr = c[USER] as String? ?: throw Exception("未填写用户")
        if (userStr.length > 32)
            throw Exception("用户超长")

        val ivStr = c[IV] as String? ?: throw Exception("未填写 IV")
        if (ivStr.length > 12)
            throw Exception("IV 超长")

        val keyStr = c[KEY] as String? ?: throw Exception("未填写 Key")
        if (keyStr.length > 32)
            throw Exception("Key 超长")

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
