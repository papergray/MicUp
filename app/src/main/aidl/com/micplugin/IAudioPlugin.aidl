// IAudioPlugin.aidl — Contract for third-party plugin APKs
// Plugin APKs must implement a Service with action "com.micplugin.AUDIO_PLUGIN"
// and bind to this interface.
package com.micplugin;

interface IAudioPlugin {
    /**
     * Returns JSON-encoded plugin metadata:
     * { "name": "...", "version": "...", "author": "...",
     *   "params": [{"id": 0, "name": "Threshold", "min": -60.0, "max": 0.0, "default": -20.0}] }
     */
    String getPluginInfo();

    /**
     * Process audio buffer in-place.
     * @param buffer  float[] of interleaved mono samples
     * @param frames  number of frames (== buffer.length for mono)
     * @param sampleRate current sample rate
     * @return true if processing succeeded
     */
    boolean processBuffer(inout float[] buffer, int frames, int sampleRate);

    /** Set a parameter value by ID */
    void setParameter(int id, float value);

    /** Get a parameter value by ID */
    float getParameter(int id);

    /** Called when the audio engine starts */
    void onStart(int sampleRate, int framesPerBuffer);

    /** Called when the audio engine stops */
    void onStop();

    /**
     * Optional: return fully-qualified class name of a custom UI Activity,
     * or empty string to use auto-generated parameter UI.
     */
    String getEditorClassName();
}
