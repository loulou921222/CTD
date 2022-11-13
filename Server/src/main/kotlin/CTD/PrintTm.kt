package CTD.plugins

import java.text.SimpleDateFormat
import java.util.*

fun printtm(string: String) {
    val timeStamp: String = SimpleDateFormat("HH:mm:ss").format(Date())
    println("[$timeStamp] $string")
}