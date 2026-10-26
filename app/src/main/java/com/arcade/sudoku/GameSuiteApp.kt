package com.arcade.sudoku

import com.arcade.sudoku.game.grid.GridWords
import com.arcade.sudoku.game.words.WordList

import android.app.Application
import com.arcade.sudoku.push.Push

class GameSuiteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Push.init(this)
        WordList.load(this)
        // Letter Grid's longer list loads in the background.
        Thread { GridWords.load(this) }.start()
    }
}
