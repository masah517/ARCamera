package com.masa.arcamera.ui

import android.Manifest.permission.CAMERA
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.ContentValues
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.SceneView
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import com.masa.arcamera.R
import android.view.PixelCopy
import android.os.HandlerThread

import android.graphics.Bitmap
import android.media.MediaActionSound
import android.net.Uri
import android.os.Handler
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope

import com.google.ar.sceneform.rendering.ModelRenderable
import com.gorisse.thomas.sceneform.scene.await
import com.masa.arcamera.databinding.FragmentMainBinding
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

class MainFragment : Fragment() {

    private lateinit var binding: FragmentMainBinding

    private lateinit var arFragment: ArFragment
    private val arSceneView
        get() = arFragment.arSceneView
    private val arScene
        get() = arSceneView.scene

    private var model: Renderable? = null
    private var modelView: ViewRenderable? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    companion object {

        private const val FILENAME = "sample.png"
        private const val MIMETYPE = "image/png"

        @JvmStatic
        fun newInstance() =
            MainFragment().apply {
                arguments = Bundle().apply {
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requestPermissionLauncher.launch(
            arrayOf(WRITE_EXTERNAL_STORAGE, CAMERA)
        )

        arFragment = (childFragmentManager.findFragmentById(R.id.arFragment) as ArFragment).apply {
            setOnSessionConfigurationListener { session, config ->
            }

            setOnViewCreatedListener { arSceneView ->
                arSceneView.setFrameRateFactor(SceneView.FrameRate.FULL)
            }

            setOnTapArPlaneListener(::onPlaneTapped)
        }

        binding.photoButton.setOnClickListener {
            val sound = MediaActionSound()
            sound.load(MediaActionSound.SHUTTER_CLICK)
            //シャッター音の追加
            sound.play(MediaActionSound.SHUTTER_CLICK)
            takePhoto()
        }

        lifecycleScope.launchWhenCreated {
            fetchModels()
        }
    }

    private suspend fun fetchModels() {
        //ARモデルのローディング
        model = ModelRenderable.builder()
            .setSource(context, Uri.parse("models/tree.glb"))
            .setIsFilamentGltf(true)
            .await()
    }

    /**
     * Scene空間がタップされた時に呼び出されるメソッド
     * @param hitResult: ユーザーがタップした空間の点に関連する情報を持ったオブジェクト
     * @param plane: ARモデルをSceneに配置する平面
     * @param motionEvent: ユーザーのジェスチャー(Pinch、Rotate、Dragなど)を処理するイベントクラス
     */
    private fun onPlaneTapped(hitResult: HitResult, plane: Plane, motionEvent: MotionEvent) {
        if (model == null) {
            Toast.makeText(context, "Loading AR Model...", Toast.LENGTH_SHORT).show()
            return
        }

        initScene(hitResult)
    }

    private fun initScene(hitResult: HitResult){
        arScene.addChild(AnchorNode(hitResult.createAnchor()).apply {
            addChild(TransformableNode(arFragment.transformationSystem).apply {
                renderable = model
                renderableInstance.animate(true).start()
            })
        })
    }

    private fun takePhoto() {

        // SceneViewと同じサイズのBitmapを作成する
        val bitmap = setBitMapConfig()
        captureScreen(bitmap)
    }

    private fun setBitMapConfig(): Bitmap = Bitmap.createBitmap(
        arSceneView.width, arSceneView.height,
        Bitmap.Config.ARGB_8888
    )

    /**
     * 表示している画面のsクリーンキャプチャを実行するメソッド
     * @param bitmap: 保存したい画像のBitmap
     */
    private fun captureScreen(bitmap: Bitmap) {

        // Handlerを作成して、画像のローディングをオフロードする
        val pixelHandlerThread = HandlerThread("PixelCopierThread")
        pixelHandlerThread.start()

        // Make the request to copy.
        PixelCopy.request(arSceneView, bitmap, { copyResult ->
            if (copyResult === PixelCopy.SUCCESS) {
                try {
                    saveBitmapToDisk(bitmap, "")
                } catch (e: IOException) {
                    Toast.makeText(context, e.toString(), Toast.LENGTH_LONG).show()
                    return@request
                }
            } else {
                val toast =
                    Toast.makeText(context, "Failed to copyPixels: $copyResult", Toast.LENGTH_LONG)
                toast.show()
            }
            pixelHandlerThread.quitSafely()
        }, Handler(pixelHandlerThread.looper))
    }

    /**
     * BitMapを端末に保存する処理
     * @param bitmap: 保存したい画像のBitmap
     * @param filename: 保存したい画像のファイル名
     */
    @Throws(IOException::class)
    private fun saveBitmapToDisk(bitmap: Bitmap, filename: String) {
        val out = File(filename)
        out.parentFile?.let {
            if (it.exists()) {
                it.mkdirs()
            }
        }

        try {
            val contentResolver = context?.contentResolver

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, FILENAME)
                put(MediaStore.Images.Media.MIME_TYPE, MIMETYPE)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val collection =
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val item = contentResolver?.insert(collection, values)!!

            contentResolver.openFileDescriptor(item, "w", null).use {
                FileOutputStream(it!!.fileDescriptor).use { outputStream ->
                    ByteArrayOutputStream().use { outputData ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputData)
                        outputData.writeTo(outputStream)
                        outputStream.flush()
                        outputStream.close()
                    }
                }
            }

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(item, values, null, null)
        } catch (ex: IOException) {
            throw IOException("Failed to save bitmap to disk", ex)
        }
    }
}
