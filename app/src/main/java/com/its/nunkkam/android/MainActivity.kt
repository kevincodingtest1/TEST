// 패키지 선언: 이 코드가 속한 패키지를 지정
package com.its.nunkkam.android

// 필요한 라이브러리들을 가져오기
import android.Manifest // 안드로이드 권한 관련 클래스
import android.annotation.SuppressLint
import android.content.pm.PackageManager // 패키지 관리자 클래스
import android.graphics.* // 그래픽 관련 클래스들
import android.os.Bundle // 액티비티 생명주기 관련 클래스
import android.util.Log // 로그 출력을 위한 클래스
import android.widget.ImageView // 이미지 뷰 위젯
import android.widget.TextView // 텍스트 뷰 위젯
import android.widget.Toast // 짧은 메시지를 화면에 표시하는 클래스
import androidx.appcompat.app.AppCompatActivity // 앱 호환성을 위한 기본 액티비티 클래스
import androidx.camera.core.* // 카메라 관련 핵심 클래스들
import androidx.camera.lifecycle.ProcessCameraProvider // 카메라 프로바이더 클래스
import androidx.camera.view.PreviewView // 카메라 미리보기 뷰 클래스
import androidx.core.app.ActivityCompat // 액티비티 호환성 관련 클래스
import androidx.core.content.ContextCompat // 컨텍스트 호환성 관련 클래스
import com.google.mediapipe.framework.image.BitmapImageBuilder // MediaPipe 이미지 빌더 클래스
import com.google.mediapipe.tasks.core.BaseOptions // MediaPipe 기본 옵션 클래스
import com.google.mediapipe.tasks.vision.core.RunningMode // MediaPipe 실행 모드 클래스
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker // 얼굴 랜드마크 감지 클래스
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult // 얼굴 랜드마크 결과 클래스
import java.io.ByteArrayOutputStream // 바이트 배열 출력 스트림 클래스
import java.util.concurrent.ExecutorService // 실행자 서비스 인터페이스
import java.util.concurrent.Executors // 실행자 생성 유틸리티 클래스
import kotlin.math.abs // 절대값을 계산하는 수학 함수

// 주석 규칙 | [외부]: 외부 데이터에서 가저오는 부분을 구분하기 위한 주석

// MainActivity 클래스 정의: 앱의 메인 화면을 담당
class MainActivity : AppCompatActivity() {

    // 클래스 내부에서 사용할 변수들을 선언
    private lateinit var faceLandmarker: FaceLandmarker // 얼굴 랜드마크 감지기
    private lateinit var cameraExecutor: ExecutorService // 카메라 작업 실행을 위한 실행자
    private lateinit var viewFinder: PreviewView // 카메라 미리보기 뷰
    private lateinit var eyeStatusImageView: ImageView // 눈 상태를 표시할 이미지 뷰
    private lateinit var eyeStatusTextView: TextView // 눈 상태를 표시할 텍스트 뷰
    private var blinkCount = 0 // 눈 깜빡임 총 횟수를 저장하는 변수
    private var lastEyeState = true // 마지막으로 감지된 눈 상태 (true: 눈 뜸, false: 눈 감음)
    private lateinit var blinkCountTextView: TextView // 눈 깜빡임 횟수를 표시할 TextView
    private var lastBlinkTime = System.currentTimeMillis() // 마지막 눈 깜빡임이 감지된 시간
    private var blinkRate = 0.0 // 분당 눈 깜빡임 횟수 (blinks per minute)
    private lateinit var blinkRateTextView: TextView // 분당 눈 깜빡임 횟수를 표시할 TextView
    private var frameCounter = 0 // 프레임 카운터
    private var lastFpsUpdateTime = System.currentTimeMillis() // 마지막 FPS 업데이트 시간
    private var fps = 0f // 현재 FPS
    private lateinit var fpsTextView: TextView // FPS를 표시할 TextView

    // 액티비티가 생성될 때 호출되는 함수
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // 부모 클래스의 onCreate 메서드 호출
        setContentView(R.layout.activity_main) // [외부] 레이아웃 XML 파일을 가져와 화면에 설정

        // XML에서 정의한 뷰들을 찾아 변수에 할당
        viewFinder = findViewById(R.id.viewFinder)
        eyeStatusImageView = findViewById(R.id.eyeStatusImageView)
        eyeStatusTextView = findViewById(R.id.textViewEyeStatus)
        fpsTextView = findViewById(R.id.fpsTextView)

        // 카메라 권한이 있는지 확인하고, 있으면 카메라 시작, 없으면 권한 요청
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        // 카메라 작업을 위한 단일 스레드 실행자 생성
        cameraExecutor = Executors.newSingleThreadExecutor()

        // FaceLandmarker 설정
        val baseOptions = BaseOptions.builder() // [외부] 얼굴 랜드마크 모델 파일(face_landmarker.task) 가져오기
            .setModelAssetPath("face_landmarker.task") // 모델 파일 경로 설정
            .build() // BaseOptions 객체 생성 및 반환
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions) // 기본 옵션 설정
            .setRunningMode(RunningMode.LIVE_STREAM) // 실시간 스트림 모드로 설정
            .setNumFaces(1) // 감지할 얼굴 수 설정
            .setResultListener(this::handleFaceLandmarkerResult) // 결과 처리 리스너 설정
            .build() // FaceLandmarkerOptions 객체 생성 및 반환
        faceLandmarker = FaceLandmarker.createFromOptions(this, options) // FaceLandmarker 객체 생성

        // 깜빡임 카운트 표시 관련 TextView 초기화 및 UI 업데이트
        blinkCountTextView = findViewById(R.id.blinkCountTextView)
        blinkRateTextView = findViewById(R.id.blinkRateTextView)
        updateBlinkUI()
    }

    // 카메라 시작 함수
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this) // [외부] 카메라 제공자(provider)의 인스턴스를 비동기적으로 가져오기

        //  cameraProviderFuture의 리스너는 카메라 설정 및 바인딩을 수행
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get() // cameraProviderFuture가 완료되면 실행될 cameraProviderFuture의 리스너에 추가

            // 카메라 미리보기 설정
            val preview = Preview.Builder()
                .build() // Preview 객체 생성 및 반환
                .also {
                    // also를 사용하여 생성된 Preview 객체에 대해 추가 작업 수행
                    // setSurfaceProvider를 호출하여 카메라 미리보기를 표시할 surface 제공
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            // 이미지 분석 설정
            val imageAnalyzer = ImageAnalysis.Builder()
                .build() // ImageAnalysis 객체 생성 및 반환
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy -> // [외부] 카메라 이미지 프레임
                        val bitmap = imageProxy.toBufferBitmap() // ImageProxy를 Bitmap으로 변환
                        val mpImage = BitmapImageBuilder(bitmap).build() // MediaPipe 이미지로 변환
                        faceLandmarker.detectAsync(mpImage, imageProxy.imageInfo.timestamp) // 비동기로 얼굴 감지
                        imageProxy.close() // ImageProxy 리소스 해제
                    }
                }

            try {
                // 기존 카메라 사용을 모두 해제하고 새로 바인딩
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                // 카메라 사용 실패 시 에러 로그 출력
                Log.e(TAG, "카메라 바인딩 실패", exc)
            }

        }, ContextCompat.getMainExecutor(this))  // 메인 스레드에서 리스너가 실행
    }

    // FaceLandmarker 결과 처리 함수
    @Suppress("UNUSED_PARAMETER") // image 파라미터를 현재 사용하지 않음을 컴파일러에 알림
    private fun handleFaceLandmarkerResult(result: FaceLandmarkerResult, image: com.google.mediapipe.framework.image.MPImage) {
        frameCounter++ // 프레임 카운터 증가

        // FPS 계산
        val currentTime = System.currentTimeMillis() // 현재 시간 기록
        if (currentTime - lastFpsUpdateTime >= 1000) { // 1초마다 FPS 업데이트
            fps = frameCounter * 1000f / (currentTime - lastFpsUpdateTime) // FPS 계산
            frameCounter = 0 // 프레임 카운터 초기화
            lastFpsUpdateTime = currentTime // 마지막 FPS 업데이트 시간 갱신
            updateFpsUI() // FPS UI 업데이트
        }

        if (result.faceLandmarks().isEmpty()) return // 감지된 얼굴이 없으면 함수 종료

        val landmarks = result.faceLandmarks()[0] // 첫 번째 감지된 얼굴의 랜드마크
        Log.d("FaceLandmarks", "Total landmarks detected: ${landmarks.size}") // 감지된 총 랜드마크 수 로그 출력

        // 눈 관련 랜드마크 인덱스 val eyeLandmarks = listOf(159, 145, 33, 133, 386, 374, 263, 362)

        // 눈 랜드마크 좌표 가져오기
        val leftEyeTop = landmarks[159]     // 159: 왼쪽 눈 위쪽 점
        val leftEyeBottom = landmarks[145]  // 145: 왼쪽 눈 아래쪽 점
        val leftEyeInner = landmarks[33]    // 33: 왼쪽 눈 안쪽 점
        val leftEyeOuter = landmarks[133]   // 133: 왼쪽 눈 바깥쪽 점
        val rightEyeTop = landmarks[386]    // 386: 오른쪽 눈 위쪽 점
        val rightEyeBottom = landmarks[374] // 374: 오른쪽 눈 아래쪽 점
        val rightEyeInner = landmarks[263]  // 263: 오른쪽 눈 안쪽 점
        val rightEyeOuter = landmarks[362]  // 362: 오른쪽 눈 바깥쪽 점

        // 눈 개폐 정도 계산
        fun calculateEyeOpenness(top: com.google.mediapipe.tasks.components.containers.NormalizedLandmark,
                                 bottom: com.google.mediapipe.tasks.components.containers.NormalizedLandmark,
                                 inner: com.google.mediapipe.tasks.components.containers.NormalizedLandmark,
                                 outer: com.google.mediapipe.tasks.components.containers.NormalizedLandmark): Float {
            val verticalDistance = abs(top.y() - bottom.y()) // 눈의 세로 길이 계산
            val horizontalDistance = abs(outer.x() - inner.x()) // 눈의 가로 길이 계산
            return verticalDistance / horizontalDistance // 세로/가로 비율 반환 (눈 개폐 정도)
        }

        // 왼쪽, 오른쪽 눈의 개폐 정도 계산
        val leftEyeOpenness = calculateEyeOpenness(leftEyeTop, leftEyeBottom, leftEyeInner, leftEyeOuter)
        val rightEyeOpenness = calculateEyeOpenness(rightEyeTop, rightEyeBottom, rightEyeInner, rightEyeOuter)
        val averageEyeOpenness = (leftEyeOpenness + rightEyeOpenness) / 2 // 양쪽 눈의 평균 개폐 정도

        Log.d("FaceLandmarks", "Total landmarks detected: ${landmarks.size}, FPS: $fps")
        Log.d("EyeOpenness", "FPS: $fps, Left Eye: $leftEyeOpenness, Right Eye: $rightEyeOpenness, Average: $averageEyeOpenness")

        val eyesOpen = averageEyeOpenness >= BLINK_THRESHOLD // 눈이 열려있는지 여부 판단

        // 눈 깜빡임 감지 및 UI 업데이트 로직
        if (eyesOpen != lastEyeState && !eyesOpen) {
            blinkCount++ // 눈 깜빡임 횟수 증가
            val timeDiff = (currentTime - lastBlinkTime) / 1000.0 // 마지막 깜빡임과의 시간 차이를 초 단위로 계산
            blinkRate = 60.0 / timeDiff // 분당 깜빡임 횟수 계산 (60초 / 깜빡임 간격)
            lastBlinkTime = currentTime // 마지막 깜빡임 시간 업데이트
            updateBlinkUI() // UI 업데이트 함수 호출
        }
        lastEyeState = eyesOpen // 현재 눈 상태를 마지막 상태로 저장

        // 눈 상태에 따라 UI 업데이트
        if (!eyesOpen) {
            updateUI("Eye is closed", R.drawable.eye_closed)    // [외부] drawable/eye_closed.png 이미지 리소스 가져오기
        } else {
            updateUI("Eye is open", R.drawable.eye_open)        // [외부] drawable/eye_open.png 이미지 리소스 가져오기
        }
    }

    // 깜빡임 카운트 UI 업데이트 함수 | 메인 UI 스레드에서 실행되어야 하므로 runOnUiThread를 사용
    @SuppressLint("SetTextI18n")
    private fun updateBlinkUI() {
        runOnUiThread {
            blinkCountTextView.text = "Total Blinks: $blinkCount" // 총 깜빡임 횟수 표시
            blinkRateTextView.text = "Blink Rate: %.2f bpm".format(blinkRate) // 분당 깜빡임 횟수 표시
        }
    }

    // FPS UI 업데이트 함수
    @SuppressLint("SetTextI18n")
    private fun updateFpsUI() {
        runOnUiThread {
            fpsTextView.text = "FPS: %.2f".format(fps) // FPS 표시 업데이트
        }
    }

    // UI 업데이트 함수
    private fun updateUI(message: String, drawableResId: Int) {
        runOnUiThread {
            eyeStatusTextView.text = message // 텍스트 뷰 업데이트
            eyeStatusImageView.setImageResource(drawableResId) // 이미지 뷰 업데이트
        }
    }

    // 모든 필요한 권한이 허용되었는지 확인하는 함수
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        // REQUIRED_PERMISSIONS 배열의 모든 권한에 대해 확인
        // 모든 권한이 허용되었을 때만 true를 반환
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    // 권한 요청 결과 처리 함수
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera() // 모든 권한이 허용되면 카메라 시작
            } else {
                // 권한이 거부되었을 때 앱 종료
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    // 액티비티가 종료될 때 호출되는 함수
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown() // 카메라 실행자 종료
    }

    // 클래스 내부에서 사용할 상수들을 정의
    companion object {
        private const val TAG = "CameraXApp" // 로그 태그: 로그 메시지를 필터링하거나 식별하는 데 사용
        private const val BLINK_THRESHOLD = 0.5 // 눈 깜빡임 감지 임계값: 이 값보다 작으면 눈을 감은 것으로 판단
        private const val REQUEST_CODE_PERMISSIONS = 10 // 권한 요청 코드: onRequestPermissionsResult에서 이 요청을 식별하는 데 사용
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA) // 필요한 권한 목록: 이 앱이 필요로 하는 모든 권한을 포함
    }
}

// ImageProxy를 Bitmap으로 변환하는 확장 함수 | 이 함수는 YUV 형식의 이미지 데이터를 JPEG로 변환한 후 Bitmap으로 디코딩함
fun ImageProxy.toBufferBitmap(): Bitmap {
    // Y, U, V 버퍼 가져오기
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    // 각 버퍼의 남은 데이터 크기 계산
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    // YUV 데이터를 저장할 바이트 배열 생성
    val nv21 = ByteArray(ySize + uSize + vSize)

    // YUV 데이터 복사
    yBuffer.get(nv21, 0, ySize)             // Y 버퍼의 데이터를 nv21 배열의 시작 부분에 복사
    vBuffer.get(nv21, ySize, vSize)               // V 버퍼의 데이터를 nv21 배열의 ySize 위치부터 복사
    uBuffer.get(nv21, ySize + vSize, uSize) // U 버퍼의 데이터를 nv21 배열의 ySize + vSize 위치부터 복사

    // YUV 이미지 생성
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
    val out = ByteArrayOutputStream()
    // JPEG 형식으로 압축
    yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
    val imageBytes = out.toByteArray()
    // 바이트 배열을 Bitmap으로 디코딩하여 반환
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}