package CTD.plugins

import CTD.*
import io.ktor.websocket.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.*
import java.util.*
import kotlin.collections.LinkedHashSet

fun Application.configureSockets() {
    install(WebSockets) {
        // ...
    }
    routing {
        val connections = Collections.synchronizedSet<Connection?>(LinkedHashSet())
        var connectionNames: MutableList<MutableList<Any>> = mutableListOf() //[connection, name]
        var connectionStrings: MutableList<MutableList<Any>> = mutableListOf() //[connection, string]
        var kick: Connection? = null
        var playerCount = 0
        var gameState = 0 //0 - waiting in lobby; 1 - game running
        var submittedPlayerCount = 0
        var connectionAssignments: MutableList<MutableList<Connection>> = mutableListOf() //[explainer, guesser]
        var submittedGuesses: MutableList<MutableList<Any>> = mutableListOf() //[connection, guess]

        webSocket("/CTD") {
            //println("Adding user!")
            var thisConnection = Connection(this)
            connections += thisConnection
            try {
                //send("You are connected! There are ${connections.count()} users here.")
                for (frame in incoming) {
                    frame as? Frame.Text ?: continue
                    val receivedText = frame.readText()
                    val receivedData: List<String> = receivedText.split(" ", limit = 2)
                    val command = receivedData[0]
                    val data = receivedData[1]
                    if (command == "clientConnect") {
                        thisConnection.name = data
                        if (gameState == 1) {
                            thisConnection.session.send("gameStarted null")
                            thisConnection.session.close()
                            println("${thisConnection.name} cannot join as the game has already started.")
                            kick = thisConnection
                        }
                        else {
                            println("${thisConnection.name} joined.")
                            connectionNames.add(mutableListOf(thisConnection, thisConnection.name!!))
                            playerCount = connectionNames.count()
                            //println(connectionNames)

                            //update clients

                            //player names
                            var playerNamesData: MutableList<Any> = mutableListOf()
                            connectionNames.forEach {
                                playerNamesData += it[1].toString().replace("&", "&amp;").replace(" ", "&nbsp;").replace("<", "&lt;").replace(">", "&gt;") // prevent XSS (ask me how I know)
                            }
                            connections.forEach {
                                //player count
                                it.session.send("playerCount ${playerCount}")
                                //player names
                                it.session.send("players ${playerNamesData.joinToString(separator = " ")}")
                                //permission level
                                if (connections.indexOf(it) == 0) {
                                    it.session.send("permissionLevel 1")
                                }
                                else {
                                    it.session.send("permissionLevel 0")
                                }
                            }
                        }
                    }
                    /*val textWithUsername = "[${thisConnection.name}]: $receivedText"
                    connections.forEach {
                        it.session.send(textWithUsername)
                    }*/
                    if (command == "requestStart") {
                        if (playerCount < 3) {
                            println("Error: 3 or more players are required to start")
                        }
                        else {
                            gameState = 1
                            // game start
                            println("the game has started.")
                            connectionStrings = mutableListOf()
                            connections.forEach {
                                it.session.send("gameStart null")
                            }
                            GlobalScope.launch {
                                while (true) {
                                    if ((gameState == 1) && (playerCount - submittedPlayerCount == 0)) {
                                        gameState = 2
                                        connections.forEach {
                                            it.session.send("guessStart null")
                                        }
                                        println("all players have submitted.")

                                        connectionStrings.shuffle()
                                        connectionAssignments = createConnectionAssignments(connectionStrings, connections)
                                        for (index in 0 until connectionStrings.size) {
                                            val explainer = connectionAssignments[index][0]
                                            val explainerName = connectionNames[findWithConnection(explainer, connectionNames)][1]
                                            val guesser = connectionAssignments[index][1]
                                            val guesserName = connectionNames[findWithConnection(guesser, connectionNames)][1]
                                            val string = connectionStrings[index][1]
                                            explainer.session.send("guesser $guesserName")
                                            explainer.session.send("string $string")
                                            guesser.session.send("explainer $explainerName")
                                        }
                                        break
                                    }
                                    else if (gameState == 0) {
                                        break
                                    }
                                    delay(20)
                                }
                            }
                        }
                    }
                    if (command == "submitString") {
                        connectionStrings += mutableListOf(thisConnection, data)
                        submittedPlayerCount = connectionStrings.count()
                        if (playerCount - submittedPlayerCount == 1) {
                            println("waiting on 1 more player to submit their string...")
                        }
                        else {
                            println("waiting on ${playerCount - submittedPlayerCount} more players to submit their strings...")
                        }
                        connections.forEach {
                            it.session.send("playerCount ${playerCount}")
                            it.session.send("submittedPlayers ${submittedPlayerCount}")
                        }
                    }
                    if (command == "endGame") {
                        gameState = 0
                        submittedPlayerCount = 0
                        println("Leader ended game.")
                        connections.forEach {
                            it.session.send("gameEnded leaderEnded")

                            //update clients

                            //player names
                            var playerNamesData: MutableList<Any> = mutableListOf()
                            connectionNames.forEach {
                                playerNamesData += it[1].toString().replace("&", "&amp;").replace(" ", "&nbsp;").replace("<", "&lt;").replace(">", "&gt;") // prevent XSS (ask me how I know)
                            }
                            connections.forEach {
                                //player count
                                it.session.send("playerCount ${playerCount}")
                                //player names
                                it.session.send("players ${playerNamesData.joinToString(separator = " ")}")
                                //permission level
                                if (connections.indexOf(it) == 0) {
                                    it.session.send("permissionLevel 1")
                                }
                                else {
                                    it.session.send("permissionLevel 0")
                                }
                            }
                        }
                    }
                    if (command == "checkAnswers") {
                        println("a")
                        submittedGuesses = mutableListOf()
                        connections.forEach {
                            it.session.send("requestGuess null")
                        }
                        GlobalScope.launch {
                            while (true) {
                                if ((gameState == 2) && (playerCount - submittedGuesses.size == 0)) {
                                    println("Answers submitted.")
                                    var correctAnswers = 0
                                    for (guessesi in 0 until submittedGuesses.size) {
                                        for (answerStringi in 0 until connectionAssignments.size) {
                                            if (connectionAssignments[answerStringi][1] == submittedGuesses[guessesi][0]) {
                                                if (submittedGuesses[guessesi][1] == connectionStrings[answerStringi][1]) {
                                                    correctAnswers += 1
                                                }
                                            }
                                        }
                                    }
                                    if (correctAnswers == submittedGuesses.size) {
                                        println("One or more answers is/are incorrect!")
                                        connections.forEach {
                                            it.session.send("correct null")
                                        }
                                        gameState = 0
                                        submittedPlayerCount = 0
                                    }
                                    else {
                                        println("All answers correct!")
                                        connections.forEach {
                                            it.session.send("incorrect null")
                                        }
                                    }
                                    break
                                }
                                delay(20)
                            }
                        }
                    }
                    if (command == "submitGuess") {
                        println("b")
                        submittedGuesses += mutableListOf(thisConnection, data)
                    }
                }
            } catch (e: Exception) {
                println(e.localizedMessage)
            } finally {
                //println("$thisConnection disconnected.")
                if (thisConnection == kick) {
                    kick = null
                }
                else {
                    println("${thisConnection.name} left.")
                }
                connections -= thisConnection
                connectionNames -= mutableListOf(thisConnection, thisConnection.name!!)
                playerCount = connectionNames.count()
                //println(connectionNames)

                //update clients

                if (gameState == 0) {
                    //player names
                    var playerNamesData: MutableList<String> = mutableListOf()
                    connectionNames.forEach {
                        playerNamesData += it[1].toString().replace("&", "&amp;").replace(" ", "&nbsp;").replace("<", "&lt;").replace(">", "&gt;") // prevent XSS (ask me how I know)
                    }
                    connections.forEach {
                        //player count
                        it.session.send("playerCount ${connections.count()}")
                        //player names
                        it.session.send("players ${playerNamesData.joinToString(separator = " ")}")
                        //permission level
                        if (connections.indexOf(it) == 0) {
                            it.session.send("permissionLevel 1")
                        }
                        else {
                            it.session.send("permissionLevel 0")
                        }
                    }
                }
                //if <3 players, go back to previous game state
                if (((gameState == 1) || (gameState == 2)) && (playerCount < 3)) {
                    gameState = 0
                    submittedPlayerCount = 0
                    println("Game has ended as there are no longer enough players.")
                    connections.forEach {
                        it.session.send("gameEnded notEnoughPlayers")

                        //update clients

                        //player names
                        var playerNamesData: MutableList<Any> = mutableListOf()
                        connectionNames.forEach {
                            playerNamesData += it[1].toString().replace("&", "&amp;").replace(" ", "&nbsp;").replace("<", "&lt;").replace(">", "&gt;") // prevent XSS (ask me how I know)
                        }
                        connections.forEach {
                            //player count
                            it.session.send("playerCount ${playerCount}")
                            //player names
                            it.session.send("players ${playerNamesData.joinToString(separator = " ")}")
                            //permission level
                            if (connections.indexOf(it) == 0) {
                                it.session.send("permissionLevel 1")
                            }
                            else {
                                it.session.send("permissionLevel 0")
                            }
                        }
                    }
                }
                //player leave while entering strings
                if (gameState == 1) {
                    //remove from connectionStrings if inputted
                    var connectionStringsIndex = 0
                    for (it in connectionStrings) {
                        if (thisConnection == it[0]) {
                            break
                        }
                        connectionStringsIndex += 1
                    }
                    if (connectionStringsIndex != connectionStrings.size) {
                        connectionStrings -= connectionStrings[connectionStringsIndex]
                    }
                    submittedPlayerCount = connectionStrings.count()
                    if (playerCount - submittedPlayerCount == 1) {
                        println("waiting on 1 more player to submit their string...")
                    }
                    else {
                        println("waiting on ${playerCount - submittedPlayerCount} more players to submit their strings...")
                    }
                    connections.forEach {
                        it.session.send("playerCount ${playerCount}")
                        it.session.send("submittedPlayers ${submittedPlayerCount}")
                        //permission level
                        if (connections.indexOf(it) == 0) {
                            it.session.send("permissionLevel 1")
                        }
                        else {
                            it.session.send("permissionLevel 0")
                        }
                    }
                }
                //player leave during game
                if (gameState == 2) {
                    var oldConnectionStrings = connectionStrings.toMutableList()
                    //remove from connectionStrings
                    connectionStrings -= connectionStrings[findWithConnection(thisConnection, connectionStrings)]
                    var oldConnectionAssignments = connectionAssignments.toMutableList()
                    connectionAssignments = createConnectionAssignments(connectionStrings, connections)
                    connections.forEach {
                        var guesserChanged = 0
                        var explainerChanged = 0
                        var stringChanged = 0
                        var answerChanged = 0
                        for (oldConnectionAssignmentIndex in 0 until oldConnectionAssignments.size) {
                            //check your guesser
                            if (it == oldConnectionAssignments[oldConnectionAssignmentIndex][0]) {
                                for (newConnectionAssignmentIndex in 0 until connectionAssignments.size) {
                                    if (it == connectionAssignments[newConnectionAssignmentIndex][0]) {
                                        if (oldConnectionAssignments[oldConnectionAssignmentIndex][1] != connectionAssignments[newConnectionAssignmentIndex][1]) {
                                            guesserChanged = 1
                                        }
                                        if (oldConnectionStrings[oldConnectionAssignmentIndex][1] != connectionStrings[newConnectionAssignmentIndex][1]) {
                                            stringChanged = 1
                                        }
                                    }
                                }
                            }
                            //check your explainer has changed
                            if (it == oldConnectionAssignments[oldConnectionAssignmentIndex][1]) {
                                for (newConnectionAssignmentIndex in 0 until connectionAssignments.size) {
                                    if (it == connectionAssignments[newConnectionAssignmentIndex][1]) {
                                        if (oldConnectionAssignments[oldConnectionAssignmentIndex][0] != connectionAssignments[newConnectionAssignmentIndex][0]) {
                                            explainerChanged = 1
                                        }
                                        if (oldConnectionStrings[oldConnectionAssignmentIndex][1] != connectionStrings[newConnectionAssignmentIndex][1]) {
                                            answerChanged = 1
                                        }
                                    }
                                }
                            }
                            //permission level
                            if (connections.indexOf(it) == 0) {
                                it.session.send("permissionLevel 1")
                            }
                            else {
                                it.session.send("permissionLevel 0")
                            }
                        }

                        it.session.send("playerLeave ${guesserChanged}${explainerChanged}${stringChanged}${answerChanged} ${thisConnection.name}")

                        for (index in 0 until connectionStrings.size) {
                            val explainer = connectionAssignments[index][0]
                            val explainerName = connectionNames[findWithConnection(explainer, connectionNames)][1]
                            val guesser = connectionAssignments[index][1]
                            val guesserName = connectionNames[findWithConnection(guesser, connectionNames)][1]
                            val string = connectionStrings[index][1]
                            explainer.session.send("guesser $guesserName")
                            explainer.session.send("string $string")
                            guesser.session.send("explainer $explainerName")
                        }
                    }
                }
            }
        }
    }
}

fun createConnectionAssignments(connectionStrings: MutableList<MutableList<Any>>, connections: Set<Connection>): MutableList<MutableList<Connection>> {
    val shift = connectionStrings.size - 2
    var output: MutableList<MutableList<Connection>> = mutableListOf()
    for (index in 0 until connectionStrings.size) {
        var explainerIndex = index + shift
        var guesserIndex = explainerIndex + 1
        if (explainerIndex > connectionStrings.size - 1) {
            explainerIndex -= connectionStrings.size
        }
        if (guesserIndex > connectionStrings.size - 1) {
            guesserIndex -= connectionStrings.size
        }
        val explainer: Connection = toConnection(connectionStrings[explainerIndex][0], connections)!!
        val guesser: Connection = toConnection(connectionStrings[guesserIndex][0], connections)!!

        output += mutableListOf(explainer, guesser)
    }
    return output
}

fun toConnection(anyConnection: Any, connections: Set<Connection>): Connection? {
    for (index in connections.indices) {
        var compairable: Any = connections.elementAt(index)
        if (compairable == anyConnection) {
            return connections.elementAt(index)
        }
    }
    return null
}

fun findWithConnection(comparableConnection: Any, connectionStrings: MutableList<MutableList<Any>>): Int {
    var index = 0
    connectionStrings.forEach {
        if(it[0] == comparableConnection) {
            return index
        }
        index += 1
    }
    return -1
}
