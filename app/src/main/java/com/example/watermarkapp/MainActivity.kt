package com.example.watermarkapp

import android.content.Context
import android.graphics.*
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                WatermarkApp()
            }
        }
    }
}

data class PhotoExif(
    val model: String = "Unknown Camera",
    val focalLength: String = "0mm",
    val aperture: String = "f/0",
    val shutterSpeed: String = "1/0s",
    val iso: String = "ISO 0",
    val dateTime: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkApp() {
    val context = LocalContext.current
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var nickname by remember { mutableStateOf("nikename") }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var originalFilePath by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    LaunchedEffect(selectedImageUri, nickname) {
        selectedImageUri?.let { uri ->
            val bitmap = loadBitmapFromUri(context, uri)
            originalFilePath = getFilePathFromUri(context, uri)
            if (bitmap != null) {
                val exif = readExif(context, uri)
                processedBitmap = addWatermark(context, bitmap, exif, nickname)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("水印相机") },
                actions = {
                    if (processedBitmap != null) {
                        TextButton(onClick = {
                            saveBitmap(context, processedBitmap!!, originalFilePath)
                        }) {
                            Text("导出", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = { launcher.launch("image/*") }) {
                Text("选择图片")
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("输入昵称") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            processedBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(it.width.toFloat() / it.height.toFloat()),
                    contentScale = ContentScale.Fit
                )
            } ?: Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(Color.LightGray),
                contentAlignment = Alignment.Center
            ) {
                Text("未选择图片")
            }
        }
    }
}

fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        BitmapFactory.decodeStream(inputStream)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun getFilePathFromUri(context: Context, uri: Uri): String? {
    var filePath: String? = null
    val projection = arrayOf(MediaStore.Images.Media.DATA)
    val cursor = context.contentResolver.query(uri, projection, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            filePath = it.getString(columnIndex)
        }
    }
    return filePath
}

fun readExif(context: Context, uri: Uri): PhotoExif {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val exif = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            ExifInterface(inputStream!!)
        } else {
            // Older versions might need a real path
            return PhotoExif()
        }

        val model = exif.getAttribute(ExifInterface.TAG_MODEL) ?: "Unknown Camera"
        
        var focalLengthStr = "0mm"
        val focalLength = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
        if (focalLength > 0) {
            focalLengthStr = "${focalLength.toInt()}mm"
        }

        var apertureStr = "f/0"
        val aperture = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
        if (aperture > 0) {
            apertureStr = "f/$aperture"
        }

        var exposureStr = "1/0s"
        val exposureTime = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
        if (exposureTime > 0) {
            exposureStr = if (exposureTime < 1.0) {
                "1/${(1.0 / exposureTime).toInt()}s"
            } else {
                "${exposureTime}s"
            }
        }

        val iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS) ?: "0"
        
        var dateTimeStr = ""
        val dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME)
        if (dateTime != null) {
            try {
                val parser = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
                val formatter = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
                val date = parser.parse(dateTime)
                if (date != null) {
                    dateTimeStr = formatter.format(date)
                }
            } catch (e: Exception) {
                dateTimeStr = dateTime
            }
        }

        PhotoExif(
            model = model,
            focalLength = focalLengthStr,
            aperture = apertureStr,
            shutterSpeed = exposureStr,
            iso = "ISO $iso",
            dateTime = dateTimeStr
        )
    } catch (e: Exception) {
        e.printStackTrace()
        PhotoExif()
    }
}

fun addWatermark(context: Context, original: Bitmap, exif: PhotoExif, nickname: String): Bitmap {
    val width = original.width
    val height = original.height
    
    // Calculate watermark height (13% of image height)
    val watermarkHeight = (height * 0.13).toInt()
    val totalHeight = height + watermarkHeight
    
    val result = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    
    // Draw white background for the whole thing (to ensure bottom is white)
    canvas.drawColor(Color.White.toArgb())
    
    // Draw original image at top
    canvas.drawBitmap(original, 0f, 0f, null)
    
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Draw Canon Logo (Left side)
    try {
        val logoBitmap = BitmapFactory.decodeResource(context.resources, 
            context.resources.getIdentifier("canon", "drawable", context.packageName))
        if (logoBitmap != null) {
            val logoTargetHeight = (watermarkHeight * 0.7).toInt()
            val logoWidth = (logoBitmap.width.toFloat() / logoBitmap.height * logoTargetHeight).toInt()
            val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, logoWidth, logoTargetHeight, true)
            
            val marginX = (width * 0.05).toFloat()
            val centerY = height + (watermarkHeight / 2f)
            canvas.drawBitmap(scaledLogo, marginX, centerY - (logoTargetHeight / 2f), null)
        }
    } catch (e: Exception) { e.printStackTrace() }

    // Text settings
    val marginXRight = (width * 0.05).toFloat()
    val baseTextSize = watermarkHeight * 0.3f
    val subTextSize = watermarkHeight * 0.2f
    
    // Right side info
    // Top line: Model | focal aperture shutter iso
    // Bottom line: Nickname | DateTime
    
    val rightEdge = width - marginXRight
    val centerY = height + (watermarkHeight * 0.45f)
    val bottomY = height + (watermarkHeight * 0.75f)
    
    val paramsText = "${exif.focalLength}  ${exif.aperture}  ${exif.shutterSpeed}  ${exif.iso}"
    
    // Measure Column 2 width
    paint.textSize = baseTextSize
    paint.typeface = Typeface.DEFAULT_BOLD
    val paramsWidth = paint.measureText(paramsText)
    
    paint.textSize = subTextSize
    paint.typeface = Typeface.DEFAULT
    val dateTimeWidth = paint.measureText(exif.dateTime)
    
    val col2Width = maxOf(paramsWidth, dateTimeWidth)
    
    val padding = width * 0.02f
    val paramsX = rightEdge - col2Width
    val lineX = paramsX - padding
    val col1Right = lineX - padding
    
    // Column 1: Camera Model and Nickname
    paint.color = android.graphics.Color.BLACK
    paint.textSize = baseTextSize
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textAlign = Paint.Align.RIGHT
    canvas.drawText(exif.model, col1Right, centerY, paint)
    
    paint.textSize = subTextSize
    paint.color = android.graphics.Color.GRAY
    paint.typeface = Typeface.DEFAULT
    canvas.drawText(nickname, col1Right, bottomY, paint)
    
    // Vertical Line
    paint.strokeWidth = 3f
    paint.color = android.graphics.Color.LTGRAY
    canvas.drawLine(lineX, height + (watermarkHeight * 0.25f), lineX, height + (watermarkHeight * 0.75f), paint)
    
    // Column 2: Parameters and Time
    paint.textAlign = Paint.Align.LEFT
    paint.color = android.graphics.Color.BLACK
    paint.textSize = baseTextSize
    paint.typeface = Typeface.DEFAULT_BOLD
    canvas.drawText(paramsText, paramsX, centerY, paint)
    
    paint.textSize = subTextSize
    paint.color = android.graphics.Color.GRAY
    paint.typeface = Typeface.DEFAULT
    canvas.drawText(exif.dateTime, paramsX, bottomY, paint)

    return result
}

fun saveBitmap(context: Context, bitmap: Bitmap, originalPath: String?) {
    if (originalPath == null) {
        Toast.makeText(context, "无法获取原路径", Toast.LENGTH_SHORT).show()
        return
    }
    
    try {
        val file = File(originalPath)
        val parent = file.parentFile
        val name = file.nameWithoutExtension
        val ext = file.extension
        val newFile = File(parent, "${name}canon.${ext}")
        
        val out = FileOutputStream(newFile)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        out.flush()
        out.close()
        
        Toast.makeText(context, "导出成功: ${newFile.absolutePath}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun android.graphics.Color.toArgb(): Int = this
