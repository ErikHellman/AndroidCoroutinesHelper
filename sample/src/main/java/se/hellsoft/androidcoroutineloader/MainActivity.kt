package se.hellsoft.androidcoroutineloader

import android.os.Bundle
import android.os.SystemClock
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import se.hellsoft.coroutines.whenClicking

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        whenClicking(button) { loadData() } then { showResult(it) }
    }


    fun loadData(): Thing {
        // Fake loading things from network
        SystemClock.sleep(5000)
        return Thing()
    }

    fun showResult(thing: Thing) {
        textView.text = thing.toString()
    }
}

class Thing
