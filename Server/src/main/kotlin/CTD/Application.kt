package CTD

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import CTD.plugins.*
import io.ktor.server.application.*

fun main(args: Array<String>): Unit {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureRouting()
    configureSockets()
}
