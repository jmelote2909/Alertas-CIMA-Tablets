package com.example.overlayapp.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Inet4Address

class LanNetworkManager(private val context: Context) {

    companion object {
        const val PORT = 9999
        private const val TAG = "LanNetwork"
    }

    suspend fun sendUdpBroadcast(messageJson: String) {
        withContext(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.broadcast = true
                val sendData = messageJson.toByteArray(Charsets.UTF_8)
                val broadcastAddress = getBroadcastAddress()
                
                val sendPacket = DatagramPacket(sendData, sendData.size, broadcastAddress, PORT)
                socket.send(sendPacket)
                
                // Also send to global broadcast
                try {
                    val globalBroadcast = InetAddress.getByName("255.255.255.255")
                    val globalPacket = DatagramPacket(sendData, sendData.size, globalBroadcast, PORT)
                    socket.send(globalPacket)
                } catch (e: Exception) { /* ignore fallback error */ }

                // Also send to all-hosts multicast group
                try {
                    val multicastGroup = InetAddress.getByName("224.0.0.1")
                    val multicastPacket = DatagramPacket(sendData, sendData.size, multicastGroup, PORT)
                    socket.send(multicastPacket)
                } catch (e: Exception) { /* ignore fallback error */ }


            } catch (e: Exception) {
                Log.e(TAG, "Error enviando UDP broadcast", e)
            } finally {
                socket?.close()
            }
        }
    }

    suspend fun sendToIp(messageJson: String, ip: String) {
        withContext(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                val sendData = messageJson.toByteArray(Charsets.UTF_8)
                val address = InetAddress.getByName(ip)
                val sendPacket = DatagramPacket(sendData, sendData.size, address, PORT)
                socket.send(sendPacket)
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando UDP a IP específica", e)
            } finally {
                socket?.close()
            }
        }
    }

    suspend fun sendHeartbeat() {
        val json = org.json.JSONObject().apply {
            put("type", "HEARTBEAT")
            put("timestamp", System.currentTimeMillis())
            put("device", android.os.Build.MODEL)
        }
        sendUdpBroadcast(json.toString())
    }

    private fun getBroadcastAddress(): InetAddress {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                
                for (interfaceAddress in networkInterface.interfaceAddresses) {
                    val broadcast = interfaceAddress.broadcast
                    if (broadcast != null && broadcast is Inet4Address) {
                        Log.d(TAG, "Broadcast address encontrado: $broadcast via ${networkInterface.name}")
                        return broadcast
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error buscando broadcast address", e)
        }
        
        // Fallback using WifiManager (older method)
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val dhcp = wifiManager.dhcpInfo
            val broadcast = (dhcp.ipAddress and dhcp.netmask) or dhcp.netmask.inv()
            val quads = ByteArray(4)
            for (k in 0..3) {
                quads[k] = (broadcast shr k * 8 and 0xFF).toByte()
            }
            return InetAddress.getByAddress(quads)
        } catch (e: Exception) {
            Log.e(TAG, "Fallback final a 255.255.255.255")
            return InetAddress.getByName("255.255.255.255")
        }
    }
}
