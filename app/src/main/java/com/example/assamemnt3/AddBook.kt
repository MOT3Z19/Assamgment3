package com.example.assamemnt3

import android.app.Activity
import android.content.Intent
import android.content.RestrictionsManager.RESULT_ERROR
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media.MediaBrowserServiceCompat.RESULT_ERROR
import com.bumptech.glide.Glide.with
import com.example.assamemnt3.databinding.ActivityAddBookBinding
import com.google.android.gms.cast.framework.media.ImagePicker
import com.google.firebase.Timestamp
import com.google.firebase.database.ktx.database
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.*

class AddBook : AppCompatActivity() {
    private val TAG = "mot"
    private val db = Firebase.firestore
    private val rtdb = Firebase.database
    private lateinit var binding: ActivityAddBookBinding
    private var imgUri: Uri? = null
    private var videoUri: Uri? = null
    private lateinit var startForBookImageResult: ActivityResultLauncher<Intent>
    private lateinit var startForBookVideoResult: ActivityResultLauncher<Intent>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddBookBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startForBookImageResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            val resultCode = result.resultCode
            val data = result.data
            when (resultCode) {
                Activity.RESULT_OK -> {
                    imgUri = data?.data!!
                    Log.e(TAG, "imgUri: $imgUri")
                    binding.tiImgUpload.setText(imgUri.toString())
                }
                ImagePicker.RESULT_ERROR -> {
                    Toast.makeText(this, ImagePicker.getError(data), Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Toast.makeText(this, "Task Cancelled", Toast.LENGTH_SHORT).show()
                }
            }
        }

        startForBookVideoResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            val resultCode = result.resultCode
            val data = result.data
            when (resultCode) {
                RESULT_OK -> {
                    videoUri = data?.data!!
                    Log.e(TAG, "imgUri: $videoUri")
                    binding.btnAddVideo.text = videoUri.toString()
                }
                ImagePicker.RESULT_ERROR -> {
                    Toast.makeText(this, ImagePicker.getError(data), Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Toast.makeText(this, "Task Cancelled", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        binding.btnAddBook.setOnClickListener { btn ->
            val name = binding.tvBookName.text.toString()
            val author = binding.tvBookAuthor.text.toString()
            val price = binding.tvBookPrice.text.toString().toFloat()
            val rating = binding.ratingBar.rating

            if (name.isNotEmpty() && author.isNotEmpty()) {
                Toast.makeText(applicationContext, "Wait >>>", Toast.LENGTH_SHORT).show()
                btn.isEnabled = false
                val imagesStorageRef = Firebase.storage.getReference("booksImages/${UUID.randomUUID()}")
                val videosStorageRef = Firebase.storage.getReference("videosImages/${UUID.randomUUID()}")

                // 1- add image to storage
                val iStream = imgUri?.let { contentResolver.openInputStream(it) }
                val inputData = iStream?.let { getBytes(it) }
                if (inputData != null) {
                    imagesStorageRef.putBytes(inputData).addOnSuccessListener {
                        imagesStorageRef.downloadUrl.addOnSuccessListener { bookUrl ->

                            // 2- add video to storage
                            val iStream = videoUri?.let { contentResolver.openInputStream(it) }
                            val inputData = iStream?.let { getBytes(it) }
                            if (inputData != null) {
                                videosStorageRef.putBytes(inputData).addOnSuccessListener {
                                    Toast.makeText(applicationContext, "DONE", Toast.LENGTH_LONG).show()

                                    // 2- get storage url
                                    videosStorageRef.downloadUrl.addOnSuccessListener { videoUrl ->
                                        val book = Book(name, author, Timestamp.now().toDate().toString(), rating.toLong(), price.toLong(), bookUrl.toString(), videoUrl.toString())
                                        // 3- add to fireStore OR RTDB
                                        // addToFirestore(book)
                                        addToRTDB(book)
                                    }
                                }
                            }
                        }.addOnFailureListener {
                            Log.e(TAG, "onResume: ${it.message}")
                            btn.isEnabled = true
                        }
                    }.addOnFailureListener {
                        Log.e(TAG, "onResume: ${it.message}")
                        btn.isEnabled = true
                    }
                } else {
                    Toast.makeText(applicationContext, "No Image Selected!!", Toast.LENGTH_SHORT).show()
                    btn.isEnabled = true
                }
            } else {
                Toast.makeText(applicationContext, "Fill Book Name & Book Author Name", Toast.LENGTH_SHORT).show()
                btn.isEnabled = true
            }
        }

        binding.tiImgUpload.setOnFocusChangeListener { v, hasFocus ->
            getImage()
        }

        binding.btnAddVideo.setOnClickListener {
            getVideo()
        }
    }

    private fun addToRTDB(book: Book) {
        rtdb.reference.child(UUID.randomUUID().toString()).setValue(book)
            .addOnSuccessListener {
                Toast.makeText(applicationContext, "Step 3 Done, Book Was Added Successfully", Toast.LENGTH_SHORT).show()
                FCMService.sendRemoteNotification("New Book Added","${book.name} Book was added recently")
                finish()
            }.addOnFailureListener {
                Log.e(TAG, "onResume: ${it.message}")
                binding.btnAddBook.isEnabled = true
            }
    }

    private fun addToFirestore(book: Book) {
        db.collection("Books").add(book)
            .addOnSuccessListener {
                Toast.makeText(applicationContext, "Step 3 Done, Book Was Added Successfully", Toast.LENGTH_SHORT).show()
                finish()
            }.addOnFailureListener {
                Log.e(TAG, "onResume: ${it.message}")
                binding.btnAddBook.isEnabled = true
            }
    }

    @Throws(IOException::class)
    fun getBytes(inputStream: InputStream): ByteArray? {
        val byteBuffer = ByteArrayOutputStream()
        val bufferSize = 1024
        val buffer = ByteArray(bufferSize)
        var len = 0
        while (inputStream.read(buffer).also { len = it } != -1) {
            byteBuffer.write(buffer, 0, len)
        }
        return byteBuffer.toByteArray()
    }

    private fun getImage() {
        ImagePicker.with(this)
            .compress(1024)         //Final image size will be less than 1 MB(Optional)
            .maxResultSize(1080, 1080)  //Final image resolution will be less than 1080 x 1080(Optional)
            .createIntent { intent ->
                startForBookImageResult.launch(intent)
            }
    }

    private fun getVideo() {
        ImagePicker.with(this)
            .galleryOnly()
            .galleryMimeTypes(  //Exclude gif images
                mimeTypes = arrayOf(
                    "video/*"
                )
            )
            .createIntent { intent ->
                startForBookVideoResult.launch(intent)
            }
    }
}