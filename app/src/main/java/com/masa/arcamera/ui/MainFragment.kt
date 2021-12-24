package com.masa.arcamera.ui

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

class MainFragment : Fragment(R.layout.fragment_main) {

    private lateinit var arFragment: ArFragment
    private val arSceneView
        get() = arFragment.arSceneView
    private val arScene
        get() = arSceneView.scene

    private var model: Renderable? = null
    private var modelView: ViewRenderable? = null

    companion object {

        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            MainFragment().apply {
                arguments = Bundle().apply {
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arFragment = (childFragmentManager.findFragmentById(R.id.arFragment) as ArFragment).apply {
            setOnSessionConfigurationListener { session, config ->
            }

            setOnViewCreatedListener { arSceneView ->
                arSceneView.setFrameRateFactor(SceneView.FrameRate.FULL)
            }

            setOnTapArPlaneListener(::onPlaneTapped)
        }
    }

    private fun onPlaneTapped(hitResult: HitResult, plane: Plane, motionEvent: MotionEvent) {
        if (model == null ) {
            Toast.makeText(context, "Loading AR Model...", Toast.LENGTH_SHORT).show()
            return
        }

        arScene.addChild(AnchorNode(hitResult.createAnchor()).apply {
                addChild(TransformableNode(arFragment.transformationSystem).apply {
                    renderable = model
                    renderableInstance.animate(true).start()
                })
            }
        )
    }
}
