package com.micplugin.plugin.formats

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.micplugin.IAudioPlugin
import com.micplugin.plugin.ExternalPlugin
import com.micplugin.plugin.PluginDescriptor
import com.micplugin.plugin.PluginManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApkPlugin(
    private val context: Context,
    descriptor: PluginDescriptor,
) : ExternalPlugin(descriptor) {

    companion object {
        private const val TAG = "ApkPlugin"

        suspend fun bind(context: Context, descriptor: PluginDescriptor): ApkPlugin {
            val plugin = ApkPlugin(context, descriptor)
            plugin.connectAndWait()
            return plugin
        }
    }

    private var binder: IAudioPlugin? = null
    private var connection: ServiceConnection? = null

    private suspend fun connectAndWait() = suspendCancellableCoroutine<Unit> { cont ->
        val intent = Intent(PluginManager.APK_PLUGIN_ACTION).apply {
            setPackage(descriptor.path)
        }
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                binder = IAudioPlugin.Stub.asInterface(service)
                Log.i(TAG, "APK plugin connected: ${descriptor.name}")
                if (cont.isActive) cont.resume(Unit)
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                binder = null
                Log.w(TAG, "APK plugin disconnected: ${descriptor.name}")
            }
        }
        connection = conn
        val bound = context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        if (!bound) {
            cont.resumeWithException(IllegalStateException("Could not bind to ${descriptor.path}"))
        }
        cont.invokeOnCancellation { release() }
    }

    /** Called from audio callback via JVM — introduces IPC latency, use carefully */
    fun processBuffer(buffer: FloatArray, frames: Int, sampleRate: Int): Boolean {
        return try {
            binder?.processBuffer(buffer, frames, sampleRate) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "processBuffer exception: $e")
            false
        }
    }

    override fun setParam(id: Int, value: Float) {
        try { binder?.setParameter(id, value) }
        catch (e: Exception) { Log.e(TAG, "setParam: $e") }
    }

    override fun getParam(id: Int): Float {
        return try { binder?.getParameter(id) ?: 0f }
        catch (e: Exception) { 0f }
    }

    override fun release() {
        try { binder?.onStop() } catch (_: Exception) {}
        connection?.let { context.unbindService(it) }
        connection = null
        binder     = null
    }
}
