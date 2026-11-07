package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ArGLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs), GLSurfaceView.Renderer {

    private val TAG = "ArGLSurfaceView"

    var mSession: Session? = null
    private var mTextureId = -1
    private var mBackgroundRendererInitialized = false

    // Shader program for rendering the camera preview
    private var mProgram = -1
    private var mPositionAttrib = -1
    private var mTexCoordAttrib = -1

    // Buffer for camera background vertices
    private lateinit var mQuadVertices: FloatBuffer
    private lateinit var mQuadTexCoords: FloatBuffer

    // Screen dimensions
    private var mViewportWidth = 0
    private var mViewportHeight = 0

    // Callbacks to communicate with Compose UI
    var onPlaneDetected: ((List<Plane>) -> Unit)? = null
    var onAnchorUpdated: ((List<ProjectedAnchor>) -> Unit)? = null
    var onFrameImageAvailable: ((Image, Int) -> Unit)? = null
    var onTapOnPlane: ((Anchor) -> Unit)? = null

    // Track placed anchors
    private val mAnchors = mutableListOf<PlacedAnchorData>()

    // Gesture detectors for move, rotate, scale
    private val mGestureDetector: GestureDetector
    private val mScaleDetector: ScaleGestureDetector
    private var mSelectedAnchor: PlacedAnchorData? = null

    // Scale, rotation & offset adjustments for interaction
    data class PlacedAnchorData(
        val id: String,
        val anchor: Anchor,
        var scale: Float = 1.0f,
        var rotationDegrees: Float = 0f,
        var label: String = "Object",
        var translationText: String = "",
        var ipa: String = "",
        var exampleTarget: String = "",
        var exampleEn: String = "",
        var category: String = "AR",
        var difficulty: String = "Medium"
    )

    data class ProjectedAnchor(
        val id: String,
        val label: String,
        val translationText: String,
        val ipa: String,
        val exampleTarget: String,
        val exampleEn: String,
        val category: String,
        val difficulty: String,
        val x: Float,
        val y: Float,
        val isVisible: Boolean,
        val scale: Float,
        val rotation: Float
    )

    init {
        setEGLContextClientVersion(2)
        setRenderer(this)
        renderMode = RENDERMODE_CONTINUOUSLY

        // Set up gesture recognizers
        mGestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                handleTap(e.x, e.y)
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                // Dragging to move selected anchor
                mSelectedAnchor?.let { placed ->
                    performHitTest(e2.x, e2.y)?.let { newAnchor ->
                        // Replace existing anchor with the new tracked pose
                        val index = mAnchors.indexOf(placed)
                        if (index != -1) {
                            placed.anchor.detach()
                            mAnchors[index] = placed.copy(anchor = newAnchor)
                        }
                    }
                }
                return true
            }
        })

        mScaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                mSelectedAnchor?.let {
                    it.scale *= detector.scaleFactor
                    it.scale = it.scale.coerceIn(0.2f, 5.0f)
                }
                return true
            }
        })

        initQuadCoordinates()
    }

    private fun initQuadCoordinates() {
        // Quad spanning the screen in NDC
        val quadVerticesCoords = floatArrayOf(
            -1.0f, -1.0f, 0.0f,
            -1.0f,  1.0f, 0.0f,
             1.0f, -1.0f, 0.0f,
             1.0f,  1.0f, 0.0f
        )
        val quadTexCoords = floatArrayOf(
            0.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 1.0f,
            1.0f, 0.0f
        )

        mQuadVertices = ByteBuffer.allocateDirect(quadVerticesCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(quadVerticesCoords)
        mQuadVertices.position(0)

        mQuadTexCoords = ByteBuffer.allocateDirect(quadTexCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(quadTexCoords)
        mQuadTexCoords.position(0)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)

        // Generate OES texture ID for camera feed background rendering
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        mTextureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)

        // Compile shaders
        val vertexShaderSource = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
        """.trimIndent()

        val fragmentShaderSource = """
            #extension GL_OES_external_image : enable
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform samplerExternalOES u_Texture;
            void main() {
                gl_FragColor = texture2D(u_Texture, v_TexCoord);
            }
        """.trimIndent()

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource)

        mProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(mProgram, vertexShader)
        GLES20.glAttachShader(mProgram, fragmentShader)
        GLES20.glLinkProgram(mProgram)

        mPositionAttrib = GLES20.glGetAttribLocation(mProgram, "a_Position")
        mTexCoordAttrib = GLES20.glGetAttribLocation(mProgram, "a_TexCoord")

        mBackgroundRendererInitialized = true

        // Notify session
        mSession?.let {
            it.setCameraTextureName(mTextureId)
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        return shader
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        mViewportWidth = width
        mViewportHeight = height
        GLES20.glViewport(0, 0, width, height)
        mSession?.setDisplayGeometry(0, width, height)
    }

    private var lastProcessedFrameTimeMs = 0L

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val session = mSession ?: return
        if (!mBackgroundRendererInitialized) return

        try {
            // Update texture & obtain current AR Frame
            val frame = session.update()
            val camera = frame.camera

            // 1. Draw camera background preview
            if (mProgram != -1) {
                GLES20.glUseProgram(mProgram)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureId)
                GLES20.glUniform1i(GLES20.glGetUniformLocation(mProgram, "u_Texture"), 0)

                GLES20.glVertexAttribPointer(mPositionAttrib, 3, GLES20.GL_FLOAT, false, 0, mQuadVertices)
                GLES20.glVertexAttribPointer(mTexCoordAttrib, 2, GLES20.GL_FLOAT, false, 0, mQuadTexCoords)

                GLES20.glEnableVertexAttribArray(mPositionAttrib)
                GLES20.glEnableVertexAttribArray(mTexCoordAttrib)

                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

                GLES20.glDisableVertexAttribArray(mPositionAttrib)
                GLES20.glDisableVertexAttribArray(mTexCoordAttrib)
            }

            // 2. Fetch detected planes and notify Compose HUD
            val planes = session.getAllTrackables(Plane::class.java).filter { it.trackingState == TrackingState.TRACKING }
            Handler(Looper.getMainLooper()).post {
                onPlaneDetected?.invoke(planes)
            }

            // 3. Project placed 3D Anchors onto 2D screen coordinate pixels
            val projectedList = mutableListOf<ProjectedAnchor>()
            for (placed in mAnchors) {
                if (placed.anchor.trackingState == TrackingState.TRACKING) {
                    val pose = placed.anchor.pose
                    val projPoint = project3DTo2D(pose, camera)
                    if (projPoint != null) {
                        projectedList.add(
                            ProjectedAnchor(
                                id = placed.id,
                                label = placed.label,
                                translationText = placed.translationText,
                                ipa = placed.ipa,
                                exampleTarget = placed.exampleTarget,
                                exampleEn = placed.exampleEn,
                                category = placed.category,
                                difficulty = placed.difficulty,
                                x = projPoint[0],
                                y = projPoint[1],
                                isVisible = projPoint[2] > 0, // In front of camera
                                scale = placed.scale,
                                rotation = placed.rotationDegrees
                            )
                        )
                    }
                }
            }
            Handler(Looper.getMainLooper()).post {
                onAnchorUpdated?.invoke(projectedList)
            }

            // 4. Periodically process frame images for ML Kit Object Detection / OCR
            val now = System.currentTimeMillis()
            if (now - lastProcessedFrameTimeMs > 1200) { // Limit frequency for performance
                try {
                    val image = frame.acquireCameraImage()
                    val rotationDegrees = camera.imageIntrinsics.imageDimensions[0] // approximation or display rotation
                    Handler(Looper.getMainLooper()).post {
                        onFrameImageAvailable?.invoke(image, rotationDegrees)
                    }
                    lastProcessedFrameTimeMs = now
                } catch (e: Exception) {
                    // Ignore transient frame acquisition failures
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in onDrawFrame", e)
        }
    }

    /**
     * Projects a 3D Pose in world coordinates to 2D pixel coordinates on the screen.
     */
    private fun project3DTo2D(pose: Pose, camera: Camera): FloatArray? {
        val projMatrix = FloatArray(16)
        val viewMatrix = FloatArray(16)
        
        // Get projection and view matrices
        camera.getProjectionMatrix(projMatrix, 0, 0.1f, 100.0f)
        camera.getViewMatrix(viewMatrix, 0)

        // Multiply matrices: MVP = P * V
        val mvpMatrix = FloatArray(16)
        android.opengl.Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        // Pose position vector in homogeneous coordinates [x, y, z, 1]
        val x = pose.tx()
        val y = pose.ty()
        val z = pose.tz()
        val w = 1.0f

        // Transform coordinate: screen_coord = MVP * pose
        val result = FloatArray(4)
        android.opengl.Matrix.multiplyMV(result, 0, mvpMatrix, 0, floatArrayOf(x, y, z, w), 0)

        if (result[3] == 0.0f) return null

        // Convert homogeneous to NDC
        val ndcX = result[0] / result[3]
        val ndcY = result[1] / result[3]
        val ndcZ = result[2] / result[3]

        // Map NDC [-1, 1] to screen [0, mViewportDimensions]
        val screenX = (ndcX + 1.0f) * 0.5f * mViewportWidth
        val screenY = (1.0f - ndcY) * 0.5f * mViewportHeight

        return floatArrayOf(screenX, screenY, ndcZ)
    }

    /**
     * Performs a standard ARCore Plane hit test at the tap coordinate.
     */
    fun performHitTest(x: Float, y: Float): Anchor? {
        val session = mSession ?: return null
        val frame = try { session.update() } catch (e: Exception) { return null }
        
        val hits = frame.hitTest(x, y)
        for (hit in hits) {
            val trackable = hit.trackable
            if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                return hit.createAnchor()
            }
        }
        return null
    }

    private fun handleTap(x: Float, y: Float) {
        val session = mSession ?: return
        
        // 1. Check if we tapped close to an existing anchor for selection or adjustment
        var foundExisting = false
        for (placed in mAnchors) {
            val camera = try { session.update().camera } catch (e: Exception) { null } ?: continue
            val proj = project3DTo2D(placed.anchor.pose, camera) ?: continue
            val dist = Math.hypot((proj[0] - x).toDouble(), (proj[1] - y).toDouble())
            if (dist < 100.0) { // tap threshold 100 pixels
                mSelectedAnchor = placed
                foundExisting = true
                Log.d(TAG, "Selected existing anchor: ${placed.label}")
                break
            }
        }

        // 2. If no existing anchor tapped, perform hit test on plane to place new object
        if (!foundExisting) {
            val newAnchor = performHitTest(x, y)
            if (newAnchor != null) {
                onTapOnPlane?.invoke(newAnchor)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        mScaleDetector.onTouchEvent(event)
        mGestureDetector.onTouchEvent(event)
        return true
    }

    /**
     * Adds an anchor to the scene.
     */
    fun addAnchor(anchor: Anchor, label: String, translation: String, ipa: String, exampleTarget: String, exampleEn: String, category: String = "AR", difficulty: String = "Beginner") {
        val data = PlacedAnchorData(
            id = java.util.UUID.randomUUID().toString(),
            anchor = anchor,
            label = label,
            translationText = translation,
            ipa = ipa,
            exampleTarget = exampleTarget,
            exampleEn = exampleEn,
            category = category,
            difficulty = difficulty
        )
        mAnchors.add(data)
    }

    /**
     * Clear all placed anchors.
     */
    fun clearAnchors() {
        for (a in mAnchors) {
            a.anchor.detach()
        }
        mAnchors.clear()
        mSelectedAnchor = null
    }

    /**
     * Setup rotate gesture programmatically
     */
    fun rotateSelectedAnchor(degrees: Float) {
        mSelectedAnchor?.let {
            it.rotationDegrees += degrees
        }
    }

    /**
     * Set scale of selected anchor
     */
    fun scaleSelectedAnchor(scaleFactor: Float) {
        mSelectedAnchor?.let {
            it.scale = (it.scale * scaleFactor).coerceIn(0.2f, 5.0f)
        }
    }

    /**
     * Release all ARCore hooks securely on exit
     */
    fun releaseAllResources() {
        clearAnchors()
        mSession = null
        mBackgroundRendererInitialized = false
    }
}
