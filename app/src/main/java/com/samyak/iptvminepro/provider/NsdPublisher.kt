package com.samyak.iptvminepro.provider

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

class NsdPublisher(private val context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var serviceInfo: NsdServiceInfo? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    fun registerService(port: Int, serviceName: String = "IPTVMinePro-Phone") {
        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = "_iptv_pair._tcp"
            this.port = port
        }
        this.serviceInfo = serviceInfo

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.d("NsdPublisher", "Service registered: ${info.serviceName}")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e("NsdPublisher", "Registration failed: errorCode $errorCode")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.d("NsdPublisher", "Service unregistered: ${info.serviceName}")
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e("NsdPublisher", "Unregistration failed: errorCode $errorCode")
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e("NsdPublisher", "Error registering NSD service", e)
        }
    }

    fun unregisterService() {
        registrationListener?.let {
            try {
                nsdManager.unregisterService(it)
            } catch (e: Exception) {
                Log.e("NsdPublisher", "Error unregistering NSD service", e)
            }
        }
        registrationListener = null
    }
}
