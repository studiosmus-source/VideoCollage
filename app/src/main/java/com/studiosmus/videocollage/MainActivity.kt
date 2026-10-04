package com.studiosmus.videocollage

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.studiosmus.videocollage.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val pickVideos = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val selected = uris.take(4)
        selected.forEach { uri ->
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { }
        }
        if (selected.size < 2) {
            Toast.makeText(this, "Scegli almeno 2 video", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        binding.collage.setVideos(selected)
        binding.status.text = "${selected.size} video • trascina o usa due dita per zoom"
        binding.saveVideo.isEnabled = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.addVideos.setOnClickListener {
            pickVideos.launch(arrayOf("video/*"))
        }
        binding.saveVideo.setOnClickListener {
            Toast.makeText(
                this,
                "Motore export in preparazione: nessun file incompleto verrà salvato.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroy() {
        binding.collage.release()
        super.onDestroy()
    }
}
