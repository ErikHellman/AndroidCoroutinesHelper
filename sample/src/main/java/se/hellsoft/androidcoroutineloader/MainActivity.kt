package se.hellsoft.androidcoroutineloader

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.SystemClock.sleep
import kotlinx.android.synthetic.main.activity_main.*
import se.hellsoft.coroutines.load
import se.hellsoft.coroutines.then
import se.hellsoft.coroutines.whenClicking

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        load {
            sleep(1000) // Add a fake delay
            return@load "Loading stuff on ${Thread.currentThread().name}!"
        } then {
            loadingResult.text = it
        }

        whenClicking(button) {
            sleep(1000) // Add a fake delay

            return@whenClicking "Loading after click on ${Thread.currentThread().name}!"
        } then {
            loadingResult.text = it
        }
    }
}
