package com.its.nunkkam.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView

class SimpleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple)

        // TextView 설정
        val textView: TextView = findViewById(R.id.simpleTextView)
        textView.text = "Hello, this is a simple TextView!"
    }
}