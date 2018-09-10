import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import javax.script.Bindings
import javax.script.ScriptContext
import javax.script.ScriptEngineManager

class Plugin (val filename: String, errStream: PrintStream?) {

    private val code = File(filename).readText()

    val commands: Map<String, (CommandParameters) -> ChatServerState>? = {
        try {
            val engine = ScriptEngineManager().getEngineByExtension("kts")

            if (engine == null){
                errStream?.println("Plugins Warning: Could not create kts scripting engine. Make sure dependencies are properly installed. The plugins will not be loaded.")
                null
            }else {
                engine.eval("mapOf<String, (CommandParameters) -> ChatServerState>($code)")
                        as Map<String, (CommandParameters) -> ChatServerState>
            }

        } catch (ex: Exception) {
            errStream?.print("Error in plugin $filename: ${ex.message}\n")
            null
        }
    }()

}