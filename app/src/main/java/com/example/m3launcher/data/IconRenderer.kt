package com.example.m3launcher.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build

/** Renders app icons full-bleed (no system mask) so our own shape can clip them. */
object IconRenderer {
    fun render(d: Drawable, px: Int, themed: Boolean, bg: Int, fg: Int): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val q = (px * 0.25f).toInt() // adaptive layers are 108dp, visible area is the centre 72dp
        if (d is AdaptiveIconDrawable) {
            val mono = if (themed && Build.VERSION.SDK_INT >= 33) d.monochrome else null
            if (mono != null) {
                c.drawColor(bg)
                mono.setTint(fg)
                mono.setBounds(-q, -q, px + q, px + q)
                mono.draw(c)
            } else {
                d.background?.let { it.setBounds(-q, -q, px + q, px + q); it.draw(c) }
                d.foreground?.let { it.setBounds(-q, -q, px + q, px + q); it.draw(c) }
            }
        } else {
            c.drawColor(if (themed) bg else 0xFFE8E8E8.toInt())
            val inset = (px * 0.16f).toInt()
            d.setBounds(inset, inset, px - inset, px - inset)
            d.draw(c)
        }
        return bmp
    }
}
