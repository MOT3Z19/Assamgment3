package com.example.assamemnt3

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.example.assamemnt3.databinding.ActivityEditBookBinding
import com.google.firebase.Timestamp
import com.google.firebase.database.ktx.database
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.*

class EditBook : AppCompatActivity() {
    private val TAG = "abd"
    private val db = Firebase.firestore
    private val rtdb = Firebase.database
    private lateinit var binding: ActivityEditBookBinding
    private var name: String = ""
    private var oldVideoUrl: String = ""
    private var imgUri: Uri? = null
    private var videoUri: Uri? = null
    private lateinit var startForBookImageResult: ActivityResultLauncher<Intent>
    private lateinit var startForBookVideoResult: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditBookBinding.inflate(layoutInflater)
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
                Activity.RESULT_OK -> {
                    videoUri = data?.data!!
                    Log.e(TAG, "imgUri: $videoUri")
                    binding.btnEditVideo.text = videoUri.toString()
                }
                ImagePicker.RESULT_ERROR -> {
                    Toast.makeText(this, ImagePicker.getError(data), Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Toast.makeText(this, "Task Cancelled", Toast.LENGTH_SHORT).show()
                }
            }
        }

        rtdb.reference.child(intent.getStringExtra("id")!!).get()
            .addOnSuccessListener {
                binding.tvBookName.setText(it.child("name").value.toString())
                binding.tvBookAuthor.setText(it.child("authorName").value.toString())
                binding.tvBookPrice.setText(it.child("price").value.toString())
                binding.tiImgUpload.setText(it.child("imageUrl").value.toString())
                binding.btnEditVideo.setText(it.child("videoUrl").value.toString())
                binding.ratingBar.rating = it.child("rate").value.toString().toFloat()
                oldVideoUrl = it.child("videoUrl").value.toString()
            }.addOnFailureListener {
                Log.e(TAG, "onFailure: ${it.message}")
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    override fun onResume() {
        super.onResume()

        binding.btnEditBook.setOnClickListener { btn ->
            name = binding.tvBookName.text.toString()
            val author = binding.tvBookAuthor.text.toString()
            val price = binding.tvBookPrice.text.toString().toFloat()
            val rating = binding.ratingBar.rating

            if (name.isNotEmpty() && author.isNotEmpty()) {
                Toast.makeText(applicationContext, "Wait >>>", Toast.LENGTH_SHORT).show()
                btn.isEnabled = false
                val imagesStorageRef = Firebase.storage.getReference("booksImages/${UUID.randomUUID()}")
                val videoStorageRef = Firebase.storage.getReference("videosImages/${UUID.randomUUID()}")

                // 1- add to storage
                val iStream = imgUri?.let { contentResolver.openInputStream(it) }
                val inputData = iStream?.let { getBytes(it) }
                if (inputData != null) {
                    imagesStorageRef.putBytes(inputData).addOnSuccessListener {
                        imagesStorageRef.downloadUrl.addOnSuccessListener { bookUrl ->

                            // 2- add video to storage
                            val iStream = videoUri?.let { contentResolver.openInputStream(it) }
                            val inputData = iStream?.let { getBytes(it) }
                            if (inputData != null) {
                                videoStorageRef.putBytes(inputData).addOnSuccessListener {
                                    Toast.makeText(applicationContext, "DONE", Toast.LENGTH_LONG).show()

                                    // 2- get storage url
                                    videoStorageRef.downloadUrl.addOnSuccessListener { videoUrl ->
                                        val book = Book(name, author, Timestamp.now().toDate().toString(), rating.toLong(), price.toLong(), bookUrl.toString(), videoUrl.toString())
                                        // 3- add to fireStore OR RTDB
                                        // addToFirestore(book)
                                        updateRTDB(book)
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
                    Toast.makeText(applicationContext, "Choose Image", Toast.LENGTH_SHORT).show()
                    btn.isEnabled = true
                }
            } else {
                Toast.makeText(applicationContext, "Fill Book name & Book Author Name", Toast.LENGTH_SHORT).show()
                btn.isEnabled = true
            }
        }

        binding.tiImgUpload.setOnFocusChangeListener { v, hasFocus ->
            getImage()
        }

        binding.btnEditVideo.setOnClickListener {
            getVideo()
        }

        binding.btnDeleteBook.setOnClickListener {
//            deleteFirestore()
            deleteRealtime()
        }
    }

    private fun deleteRealtime() {
        rtdb.reference.child(intent.getStringExtra("id")!!).setValue(null)
            .addOnSuccessListener {
                Toast.makeText(applicationContext, "Deleted Successfully", Toast.LENGTH_SHORT).show()
                FCMService.sendRemoteNotification("Alert!!, Book Deleted","${binding.tvBookName.text} Book was deleted recently")
                finish()
            }.addOnFailureListener {
                Toast.makeText(applicationContext, "Deleted Failed!!", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "onResume: ${it.message}")
            }
    }

    private fun deleteFirestore() {
        db.collection("Books").document(intent.getStringExtra("id")!!).delete().addOnSuccessListener {
            Toast.makeText(applicationContext, "Deleted Successfully", Toast.LENGTH_SHORT).show()
            FCMService.sendRemoteNotification("Alert!!, Book Deleted","${binding.tvBookName.text} Book was Deleted recently")
            finish()
        }.addOnFailureListener {
            Toast.makeText(applicationContext, "Deleted Failed!!", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "onResume: ${it.message}", )
        }
    }

    private fun updateRTDB(book: Book) {
        rtdb.reference.child(intent.getStringExtra("id")!!).setValue(book)
            .addOnSuccessListener {
                Toast.makeText(applicationContext, "Step 3 Done, Book Was Updated Successfully", Toast.LENGTH_SHORT).show()
                FCMService.sendRemoteNotification("Alert!!, Book Updated","${name} Book was updated recently")
                finish()
            }.addOnFailureListener {
                Log.e(TAG, "onResume: ${it.message}")
                binding.btnEditBook.isEnabled = true
            }
    }

    private fun updateFirestore(book: Book) {
        db.collection("Books").document(intent.getStringExtra("id")!!).set(book)
            .addOnSuccessListener {
                Toast.makeText(applicationContext, "Step 3 Done, Book Was Updated Successfully", Toast.LENGTH_SHORT).show()
                FCMService.sendRemoteNotification("Alert!!, Book Updated","${name} Book was updated recently")
                finish()
            }.addOnFailureListener {
                Log.e(TAG, "onResume: ${it.message}")
                binding.btnEditBook.isEnabled = true
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