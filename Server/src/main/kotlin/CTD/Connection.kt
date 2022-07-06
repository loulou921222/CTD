package CTD

import io.ktor.websocket.*
import java.util.concurrent.atomic.*

class Connection(val session: DefaultWebSocketSession) {
    /*companion object {
        var lastId = AtomicInteger(0)
    }
    val name = "user${lastId.getAndIncrement()}"
    */
    var name: String? = null
}