package com.pippop

import com.pippop.game.grid.GridWords
import com.pippop.game.words.WordList

import android.app.Application
import com.pippop.push.Push

class PipPopApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Push.init(this)
        WordList.load(this)
        // Letter Grid's longer list loads in the background.
        Thread { GridWords.load(this) }.start()
    }
}
