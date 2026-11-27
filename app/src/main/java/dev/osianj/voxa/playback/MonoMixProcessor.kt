package dev.osianj.voxa.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Optional "mix to mono": averages left and right and plays the result on both sides, so a voice
 * panned hard to one ear (or one earbud in) is still heard fully. Pass-through when off.
 */
@UnstableApi
class MonoMixProcessor : BaseAudioProcessor() {

    @Volatile
    var mono = false

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat = inputAudioFormat

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        if (mono && inputAudioFormat.channelCount == 2 && inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            while (inputBuffer.remaining() >= 4) {
                val left = inputBuffer.getShort().toInt()
                val right = inputBuffer.getShort().toInt()
                val mixed = ((left + right) / 2).toShort()
                out.putShort(mixed)
                out.putShort(mixed)
            }
        }
        out.put(inputBuffer) // pass-through, or any trailing partial frame
        out.flip()
    }
}
