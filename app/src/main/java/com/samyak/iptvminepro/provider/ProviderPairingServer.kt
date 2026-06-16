package com.samyak.iptvminepro.provider

import android.content.Context
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.google.gson.Gson
import com.samyak.iptvminepro.model.Provider
import com.samyak.iptvminepro.model.ProviderType
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.*

class ProviderPairingServer(
    private val context: Context,
    private val onCodeUpdated: (String) -> Unit,
    private val onTimeUpdated: (Int) -> Unit,
    private val onPairingSuccess: (String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var timerJob: Job? = null
    private val repository = ProviderRepository(context)
    private val gson = Gson()
    private var currentCode = ""
    private var nsdPublisher: NsdPublisher? = null

    init {
        nsdPublisher = NsdPublisher(context)
    }

    fun start() {
        // Start code generator timer
        timerJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                generateNewCode()
                for (secondsLeft in 30 downTo 0) {
                    onTimeUpdated(secondsLeft)
                    delay(1000)
                }
            }
        }

        // Start server socket
        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = ServerSocket(0) // dynamic port
                serverSocket = socket
                val port = socket.localPort
                
                withContext(Dispatchers.Main) {
                    nsdPublisher?.registerService(port, "IPTVMinePro-${Build.MODEL}")
                }

                while (isActive) {
                    val clientSocket = socket.accept()
                    launch {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun generateNewCode() {
        val random = Random()
        val num = random.nextInt(9000000) + 1000000 // Ensure 7 digits
        currentCode = num.toString()
        onCodeUpdated(currentCode)
    }

    private fun handleClient(client: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val out: OutputStream = client.getOutputStream()

            val requestLine = reader.readLine() ?: return
            // Format is: GET /pair?code=XXXXXXX&device=XXXX HTTP/1.1
            if (requestLine.startsWith("GET /pair")) {
                val urlParts = requestLine.split(" ")
                if (urlParts.size >= 2) {
                    val path = urlParts[1]
                    val params = parseQueryParams(path)
                    val code = params["code"]
                    val device = params["device"] ?: "Unknown TV"

                    if (code == currentCode) {
                        // Success! Send providers
                        val providers = repository.getProviders().filter { it.isActive }
                        val json = gson.toJson(providers)

                        val response = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: application/json\r\n" +
                                "Content-Length: ${json.toByteArray().size}\r\n" +
                                "Connection: close\r\n\r\n" +
                                json

                        out.write(response.toByteArray())
                        out.flush()

                        // Notify UI
                        CoroutineScope(Dispatchers.Main).launch {
                            onPairingSuccess(device)
                        }
                    } else {
                        // Invalid code
                        val errorJson = "{\"error\":\"Invalid pairing code\"}"
                        val response = "HTTP/1.1 401 Unauthorized\r\n" +
                                "Content-Type: application/json\r\n" +
                                "Content-Length: ${errorJson.toByteArray().size}\r\n" +
                                "Connection: close\r\n\r\n" +
                                errorJson
                        out.write(response.toByteArray())
                        out.flush()
                    }
                }
            } else {
                // Not found
                val response = "HTTP/1.1 404 Not Found\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n\r\n"
                out.write(response.toByteArray())
                out.flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                client.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun parseQueryParams(path: String): Map<String, String> {
        val queryIndex = path.indexOf('?')
        if (queryIndex == -1 || queryIndex >= path.length - 1) return emptyMap()
        val query = path.substring(queryIndex + 1)
        val params = mutableMapOf<String, String>()
        query.split('&').forEach { param ->
            val pair = param.split('=')
            if (pair.size == 2) {
                params[pair[0]] = pair[1]
            }
        }
        return params
    }

    fun stop() {
        timerJob?.cancel()
        serverJob?.cancel()
        nsdPublisher?.unregisterService()
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
