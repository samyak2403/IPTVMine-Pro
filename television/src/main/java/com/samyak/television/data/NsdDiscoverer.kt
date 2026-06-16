package com.samyak.television.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

class NsdDiscoverer(
    private val context: Context,
    private val onDeviceDiscovered: (NsdServiceInfo) -> Unit,
    private val onDeviceLost: (NsdServiceInfo) -> Unit
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun startDiscovery() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("NsdDiscoverer", "Discovery failed to start: errorCode $errorCode")
                try {
                    nsdManager.stopServiceDiscovery(this)
                } catch (e: Exception) {
                    // ignore
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("NsdDiscoverer", "Discovery failed to stop: errorCode $errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String) {
                Log.d("NsdDiscoverer", "Discovery started: $serviceType")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d("NsdDiscoverer", "Discovery stopped: $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d("NsdDiscoverer", "Service found: ${serviceInfo.serviceName}")
                if (serviceInfo.serviceType == "_iptv_pair._tcp." || serviceInfo.serviceType == "_iptv_pair._tcp") {
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e("NsdDiscoverer", "Resolve failed: errorCode $errorCode")
                        }

                        override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                            Log.d("NsdDiscoverer", "Service resolved: ${resolvedInfo.host}:${resolvedInfo.port}")
                            onDeviceDiscovered(resolvedInfo)
                        }
                    })
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d("NsdDiscoverer", "Service lost: ${serviceInfo.serviceName}")
                onDeviceLost(serviceInfo)
            }
        }

        try {
            nsdManager.discoverServices("_iptv_pair._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e("NsdDiscoverer", "Error starting NSD discovery", e)
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e("NsdDiscoverer", "Error stopping NSD discovery", e)
            }
        }
        discoveryListener = null
    }
}
