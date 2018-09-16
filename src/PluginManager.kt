//Author: Manuel Furia
//Student ID: 1706247

/* PluginManager.kt
 * Loads plugins as .kts files from a specified directory, and issues a warning if plugins are conflicting between
 * each other or with basic commands
 */

import java.io.File
import java.io.PrintStream

class PluginManager (val directory: String, basicCommands: Map<String, (CommandParameters) -> ChatServerState>, errStream: PrintStream? = null) {

    val commands: Map<String, (CommandParameters) -> ChatServerState> = {

        val folder = File(directory)

        if (folder.exists() && folder.isDirectory){
            val files = folder.listFiles().mapNotNull { if (it.exists() && it.isFile) it.absolutePath else null }
            val commandMaps: List<Pair<String, Map<String, (CommandParameters) -> ChatServerState>>> = files.mapNotNull {
                        val plugin = Plugin(it, errStream)
                        if (plugin.commands != null)
                            Pair(plugin.filename, plugin.commands)
                        else
                            null
            }

            //Print warning messages if a plugin redefines an already defined command
            for (i in commandMaps.indices){
                for (j in 0..(i-1)){
                    for (elem in commandMaps[i].second.keys) {
                        if (commandMaps[j].second.keys.contains(elem)){
                            errStream?.println("Plugin Warning: Plugin ${commandMaps[i].first} is overriding command ${elem} already defined by plugin ${commandMaps[j].first}")
                        }
                    }
                }
            }
            //Print warning messages if a plugin redefines a basic server command
            for (i in commandMaps.indices){
                for (elem in basicCommands) {
                    if (commandMaps[i].second.keys.contains(elem.key)){
                        errStream?.println("Plugin Warning: Plugin ${commandMaps[i].first} is overriding command ${elem.key} already defined in the basic server commands.")
                    }
                }
            }

            commandMaps.map{it.second}.fold(mapOf()){res, elem -> res + elem}

        } else {
            mapOf()
        }

    }()

}