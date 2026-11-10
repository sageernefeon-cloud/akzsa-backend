package com.nefeon.akzsa.keyboard

import android.content.Context
import android.graphics.drawable.Animatable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.nefeon.akzsa.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Inline GIF panel backed by the GIPHY API (Tenor's API has been shut down).
 *
 * The search field is a plain TextView, NOT an EditText: an EditText inside the keyboard window cannot
 * receive typing from the keyboard itself. The service routes its own letter keys into [GifController]
 * while the panel is open (same approach as the FILE AI query), and Enter / the magnifier runs the search.
 */
object GifPanel {

    // Searches go through the AKZSA server (/v1/gifs/search). The GIPHY key lives only on the server (GIPHY_API_KEY).

    private const val THUMB_WIDTH_DP = 84
    private const val THUMB_HEIGHT_DP = 52

    data class GifResult(val url: String, val previewUrl: String, val title: String)

    /** Handle the service uses to feed typing into the panel and to trigger a search. */
    class GifController {
        @Volatile var query: String = ""
        internal var render: (String) -> Unit = {}
        internal var runSearch: (String) -> Unit = {}

        fun append(text: String) { query = (query + text).take(60); render(query) }
        fun backspace() {
            if (query.isEmpty()) return
            query = query.substring(0, query.offsetByCodePoints(query.length, -1))
            render(query)
        }
        fun search() = runSearch(query)
    }

    fun build(
        context: Context,
        scope: CoroutineScope,
        controller: GifController,
        dp: (Int) -> Int,
        fetchGifs: suspend (query: String, stickers: Boolean) -> Result<List<GifResult>>,
        textColor: () -> Int,
        keyColor: () -> Int,
        borderColor: () -> Int,
        accentColor: () -> Int,
        onAccentColor: () -> Int,
        onGifSelected: (GifResult) -> Unit,
        onClose: () -> Unit,
    ): LinearLayout {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headerRow.addView(TextView(context).apply {
            text = context.getString(R.string.gif_panel_title)
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(textColor())
        }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })

        fun boxBackground() = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(keyColor())
            setStroke(dp(1), borderColor())
        }

        val searchBox = TextView(context).apply {
            textSize = 13f
            maxLines = 1
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(textColor())
            background = boxBackground()
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        fun showQuery(q: String) {
            if (q.isEmpty()) {
                searchBox.text = context.getString(R.string.gif_search_hint)
                searchBox.alpha = 0.6f
            } else {
                searchBox.text = q
                searchBox.alpha = 1f
            }
        }
        showQuery(controller.query)
        headerRow.addView(searchBox, LinearLayout.LayoutParams(0, dp(36), 1f).apply {
            setMargins(0, 0, dp(4), 0)
        })

        val goBtn = TextView(context).apply {
            text = "🔍"
            contentDescription = context.getString(R.string.cd_gif_search)
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(onAccentColor())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(accentColor())
            }
        }
        headerRow.addView(goBtn, LinearLayout.LayoutParams(dp(40), dp(36)).apply { rightMargin = dp(4) })

        val closeBtn = TextView(context).apply {
            text = "✕"
            contentDescription = context.getString(R.string.cd_gif_close)
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(textColor())
            background = boxBackground()
            setOnClickListener { onClose() }
        }
        headerRow.addView(closeBtn, LinearLayout.LayoutParams(dp(36), dp(36)))
        panel.addView(headerRow)

        val resultsScroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        // Thumbnail grid: two rows that scroll sideways together.
        val gridRows = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        val rowTop = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val rowBottom = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        gridRows.addView(rowTop, LinearLayout.LayoutParams(-2, dp(THUMB_HEIGHT_DP + 4)))
        gridRows.addView(rowBottom, LinearLayout.LayoutParams(-2, dp(THUMB_HEIGHT_DP + 4)))
        resultsScroll.addView(gridRows)
        panel.addView(resultsScroll, LinearLayout.LayoutParams(-1, dp(THUMB_HEIGHT_DP * 2 + 16)))
        fun clearGrid() { rowTop.removeAllViews(); rowBottom.removeAllViews() }

        val statusText = TextView(context).apply {
            text = context.getString(R.string.gif_trending)
            textSize = 11f
            setTextColor(textColor())
            gravity = Gravity.CENTER
        }
        panel.addView(statusText)

        panel.addView(TextView(context).apply {
            text = context.getString(R.string.gif_powered_by)
            textSize = 10f
            setTextColor(textColor())
            gravity = Gravity.END
        })

        var stickerMode = false

        fun searchGifs(query: String, preset: List<GifResult>? = null) {
            clearGrid()
            statusText.text = context.getString(R.string.gif_searching)
            scope.launch {
                try {
                    val gifs: List<GifResult> = preset
                        ?: fetchGifs(query.trim(), stickerMode).getOrElse { error ->
                            clearGrid()
                            statusText.text = error.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.gif_search_failed)
                            return@launch
                        }
                    clearGrid()
                    if (gifs.isEmpty()) {
                        statusText.text = context.getString(R.string.gif_none_found)
                    } else {
                        statusText.text = if (query.isBlank())
                            context.getString(R.string.gif_tap_to_send)
                        else
                            context.getString(R.string.gif_results_for, query.trim())
                        gifs.forEachIndexed { index, gif ->
                            val defaultTitle = context.getString(R.string.gif_default_title)
                            val label = gif.title.ifBlank { defaultTitle }
                            val image = ImageView(context).apply {
                                scaleType = ImageView.ScaleType.CENTER_CROP
                                tag = gif.previewUrl
                                // Animated previews only run while the panel is on screen (saves battery and CPU).
                                addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                                    override fun onViewAttachedToWindow(v: View) { (drawable as? Animatable)?.start() }
                                    override fun onViewDetachedFromWindow(v: View) { (drawable as? Animatable)?.stop() }
                                })
                            }
                            // Caption shows only until the thumbnail arrives (or if it never does).
                            val caption = TextView(context).apply {
                                text = label.take(24)
                                textSize = 10f
                                gravity = Gravity.CENTER
                                maxLines = 3
                                setPadding(dp(4), dp(4), dp(4), dp(4))
                                setTextColor(textColor())
                            }
                            val cell = FrameLayout(context).apply {
                                contentDescription = context.getString(R.string.cd_gif_send_item, label)
                                isClickable = true
                                clipToOutline = true
                                background = android.graphics.drawable.GradientDrawable().apply {
                                    cornerRadius = dp(6).toFloat()
                                    setColor(keyColor())
                                    setStroke(dp(1), borderColor())
                                }
                                addView(image, FrameLayout.LayoutParams(-1, -1))
                                addView(caption, FrameLayout.LayoutParams(-1, -1))
                                setOnClickListener { onGifSelected(gif) }
                                setOnLongClickListener {
                                    val now = GifStore.toggleFavourite(context, gif)
                                    statusText.text = if (now) "Added to favourites" else "Removed from favourites"
                                    true
                                }
                            }
                            val row = if (index % 2 == 0) rowTop else rowBottom
                            row.addView(cell, LinearLayout.LayoutParams(dp(THUMB_WIDTH_DP), dp(THUMB_HEIGHT_DP)).apply {
                                setMargins(dp(2), dp(2), dp(2), dp(2))
                            })
                            launch {
                                val preview = GifThumbnails.load(context.resources, gif.previewUrl, dp(THUMB_HEIGHT_DP))
                                if (preview != null && image.tag == gif.previewUrl && image.isAttachedToWindow) {
                                    image.setImageDrawable(preview)
                                    (preview as? Animatable)?.start()
                                    caption.visibility = View.GONE
                                }
                            }
                        }
                    }
                } catch (_: kotlinx.coroutines.CancellationException) {
                    // Panel closed or keyboard destroyed; nothing to show.
                } catch (_: Exception) {
                    statusText.text = context.getString(R.string.gif_search_failed)
                }
            }
        }

        val sourceRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        fun sourceChip(label: String, onClick: () -> Unit) = TextView(context).apply {
            text = label
            textSize = 11f
            setTextColor(textColor())
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(keyColor())
                setStroke(dp(1), borderColor())
            }
            setOnClickListener { onClick() }
        }
        val chipParams = { LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) } }
        sourceRow.addView(sourceChip("Trending") { stickerMode = false; searchGifs("") }, chipParams())
        sourceRow.addView(sourceChip("Recent") { stickerMode = false; searchGifs("", GifStore.recents(context)) }, chipParams())
        sourceRow.addView(sourceChip("Favourites") { searchGifs("", GifStore.favourites(context)) }, chipParams())
        sourceRow.addView(sourceChip("Stickers") { stickerMode = true; searchGifs(controller.query) }, chipParams())
        panel.addView(sourceRow, 1)

        controller.render = { showQuery(it) }
        controller.runSearch = { searchGifs(it) }
        goBtn.setOnClickListener { searchGifs(controller.query) }

        searchGifs(controller.query)   // trending when empty
        return panel
    }
}
