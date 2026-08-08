package com.multiverse.rift

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import com.multiverse.rift.game.GameState
import com.multiverse.rift.render.GameView

/**
 * Одна Activity, один View. Всё остальное рисуется на канве.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val game = GameState(prefs.getInt(KEY_BEST, 0)) { best ->
            prefs.edit().putInt(KEY_BEST, best).apply()
        }
        setContentView(GameView(this, game))
    }

    private companion object {
        const val PREFS = "rift"
        const val KEY_BEST = "best_depth"
    }
}
