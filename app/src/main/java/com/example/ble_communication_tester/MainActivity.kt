package com.example.ble_communication_tester

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val goToScan = findViewById<Button>(R.id.goToScan)
        val goToAdvertisement = findViewById<Button>(R.id.goToAdvertisement)

        goToScan.setOnClickListener {
            val intent = Intent(this, Central::class.java)
            startActivity(intent)
            finish()
        }

        goToAdvertisement.setOnClickListener {
            val intent = Intent(this, Peripheral::class.java)
            startActivity(intent)
            finish()
        }
    }
}