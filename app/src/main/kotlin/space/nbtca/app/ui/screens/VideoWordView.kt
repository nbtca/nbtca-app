package space.nbtca.app.ui.screens

import android.content.Context
import android.graphics.*
import android.media.MediaPlayer
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout

/** Hardware-decoded video beneath a fixed text cutout; no bitmap extraction or per-frame Compose state. */
internal class VideoWordView(context: Context) : FrameLayout(context), TextureView.SurfaceTextureListener {
    private val texture = TextureView(context)
    private val mask = WordMask(context)
    private var player: MediaPlayer? = null
    private var prepared = false
    private var running = false
    private var released = false
    private var firstFrameAt = 0L
    private var frames = 0
    private var measured = false

    init {
        setBackgroundColor(Color.rgb(252,252,250))
        texture.surfaceTextureListener = this
        addView(texture, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(mask, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun update(word: String, play: Boolean) {
        if (released) return
        contentDescription = word
        mask.updateWord(word)
        if (running != play) {
            running = play
            if (prepared) {
                if (play) player?.start() else player?.pause()
            }
        }
    }

    private fun prepare(surfaceTexture: SurfaceTexture) {
        if (released || player != null) return
        val media = MediaPlayer()
        player = media
        val surface = Surface(surfaceTexture)
        try {
            media.setSurface(surface)
            context.assets.openFd("card/nbtca.mp4").use { media.setDataSource(it.fileDescriptor,it.startOffset,it.length) }
            media.isLooping = true
            media.setVolume(0f,0f)
            media.setOnPreparedListener {
                if (player === it && !released) {
                    prepared = true
                    if (running) it.start()
                }
            }
            media.setOnErrorListener { failed, _, _ ->
                if (player === failed) disposePlayer()
                true
            }
            media.prepareAsync()
        } catch (_: Exception) {
            disposePlayer()
        } finally { surface.release() }
    }

    private fun disposePlayer() {
        prepared = false
        player?.setOnPreparedListener(null)
        player?.setOnErrorListener(null)
        player?.release()
        player = null
    }

    fun release() {
        released = true
        running = false
        disposePlayer()
        texture.surfaceTextureListener = null
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) = prepare(surface)
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        disposePlayer()
        return true
    }
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        if (!running || measured) return
        val now = SystemClock.elapsedRealtime()
        if (firstFrameAt == 0L) { firstFrameAt = now; frames = 0 }
        frames++
        if (now-firstFrameAt >= 5000) {
            Log.i("CardVideo", "Rendered ${frames-1} frames in ${now-firstFrameAt}ms")
            measured = true
        }
    }

    private class WordMask(context: Context) : View(context) {
        private val backgroundPaint = Paint().apply { color=Color.rgb(252,252,250) }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface=Typeface.create("sans-serif-black",Typeface.BOLD)
            xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
        private var word="CARD"
        private var x=0f
        private var baseline=0f
        init {
            // Cache the unchanged mask on the GPU; only the underlying video texture updates.
            setLayerType(LAYER_TYPE_HARDWARE,null)
            importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        fun updateWord(value: String) {
            if (word != value) { word=value; measureWord(); invalidate() }
        }
        private fun measureWord() {
            if(width <= 0 || height <= 0) return
            textPaint.textSize=height*.83f
            val textWidth=textPaint.measureText(word)
            if(textWidth>width) textPaint.textSize*=width/textWidth
            x=(width-textPaint.measureText(word))/2
            baseline=(height-textPaint.ascent()-textPaint.descent())/2
        }
        override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) { measureWord() }
        override fun onDraw(canvas: Canvas) {
            val layer=canvas.saveLayer(0f,0f,width.toFloat(),height.toFloat(),null)
            canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),backgroundPaint)
            canvas.drawText(word,x,baseline,textPaint)
            canvas.restoreToCount(layer)
        }
    }
}
