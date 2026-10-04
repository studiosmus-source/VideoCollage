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
        binding.changeLayout.isEnabled = true
        binding.changeFormat.isEnabled = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.addVideos.setOnClickListener {
            pickVideos.launch(arrayOf("video/*"))
        }
        binding.changeFormat.setOnClickListener {
            val labels = arrayOf("16:9", "9:16", "1:1", "4:5", "5:4", "3:2", "2:3")
            val ratios = floatArrayOf(16f/9f, 9f/16f, 1f, 4f/5f, 5f/4f, 3f/2f, 2f/3f)
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Formato collage")
                .setItems(labels) { _, which ->
                    binding.collage.setCanvasRatio(ratios[which])
                    binding.changeFormat.text = labels[which]
                    binding.status.text = labels[which] + " • " + binding.collage.layoutLabel()
                }
                .show()
        }
        binding.changeLayout.setOnClickListener {
            binding.collage.cycleLayout()
            binding.status.text = binding.collage.layoutLabel() + " • trascina o usa due dita per zoom"
        }
        binding.saveVideo.setOnClickListener {
            val uris = binding.collage.selectedUris()
            if (uris.size < 2) return@setOnClickListener
            binding.saveVideo.isEnabled = false
            CollageExporter(this).export(
                uris = uris,
                state = binding.collage.exportState(),
                onProgress = { message -> runOnUiThread { binding.status.text = message } },
                onDone = {
                    runOnUiThread {
                        binding.status.text = "Video salvato in Galleria"
                        binding.saveVideo.isEnabled = true
                        Toast.makeText(this, "Video salvato in Movies/VideoCollage", Toast.LENGTH_LONG).show()
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        binding.status.text = "Errore durante il salvataggio"
                        binding.saveVideo.isEnabled = true
                        Toast.makeText(this, error.message ?: "Esportazione non riuscita", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        binding.collage.release()
        super.onDestroy()
    }
}
