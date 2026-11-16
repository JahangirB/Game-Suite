package com.pippop

import com.pippop.game.golf.Courses
import com.pippop.game.grid.GridWords
import com.pippop.game.words.WordList

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.pippop.data.QuietStore
import com.pippop.data.QuietTrigger
import com.pippop.data.WorkScheduler
import com.pippop.push.Push

class PipPopApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Push.init(this)
        WordList.load(this)
        // Mini Golf's 1,000 holes load in the background.
        Courses.load(this)
        com.pippop.data.SkinStore.init(this)
        // Opens the solo records and brings Sudoku's old bests across.
        com.pippop.data.SoloRecordStore.init(this)
        // Letter Grid's longer list loads in the background.
        Thread { GridWords.load(this) }.start()
        // Update by itself: when the whole app goes to the background, a job looks for a newer build.
        val quiet = QuietTrigger(QuietStore.get(this), WorkScheduler(this), available = BuildConfig.UPDATE_BASE_URL.isNotEmpty() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = quiet.appStopped()
            override fun onStart(owner: LifecycleOwner) = quiet.appStarted()
        })
    }
}
