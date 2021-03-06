package work.airz

import javafx.application.Platform
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.*
import javafx.scene.input.DragEvent
import javafx.scene.input.TransferMode
import javafx.scene.layout.GridPane
import javafx.scene.layout.Pane
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import net.bramp.ffmpeg.FFmpeg
import net.bramp.ffmpeg.FFprobe
import net.bramp.ffmpeg.probe.FFmpegStream
import java.io.File
import java.net.URL
import java.util.*

class Controller : Initializable {
    @FXML
    private lateinit var mainpage: Pane

    @FXML
    private lateinit var fileList: ListView<Label>

    @FXML
    private lateinit var genelog: Label

    @FXML
    private lateinit var height: TextField

    @FXML
    private lateinit var width: TextField

    @FXML
    private lateinit var videorate: TextField

    @FXML
    private lateinit var ratenumerator: TextField

    @FXML
    private lateinit var ratedenominator: TextField

    @FXML
    private lateinit var audiorate: TextField

    @FXML
    private lateinit var progress: ProgressIndicator

    @FXML
    private lateinit var issameinput: CheckBox

    @FXML
    private lateinit var videosettings: GridPane

    @FXML
    private lateinit var isrotate: CheckBox

    @FXML
    private lateinit var isdeinterlace: CheckBox

    @FXML
    private lateinit var ishalfframerate: CheckBox


    override fun initialize(location: URL?, resources: ResourceBundle?) {
        height.text = 0.toString()
        width.text = 0.toString()
        videorate.text = 0.toString()
        ratenumerator.text = 0.toString()
        ratedenominator.text = 0.toString()
        audiorate.text = 0.toString()
        isdeinterlace.isSelected = false
        ishalfframerate.isSelected = false
        issameinput.isSelected = true
        isrotate.isSelected = true
        isrotate.selectedProperty().addListener { observable, oldValue, newValue ->
            if (fileList.items.size != 0) {
                loadValues(File(fileList.items.first().text), newValue)
            }
        }
        issameinput.selectedProperty().addListener { observable, oldValue, newValue ->
            videosettings.isDisable = newValue
        }

        height.textProperty().addListener { _, oldValue, newValue ->
            if (!intValidate(newValue.toString())) {
                height.text = oldValue.toString()
            }
        }
        width.textProperty().addListener { _, oldValue, newValue ->
            if (!intValidate(newValue.toString())) {
                width.text = oldValue.toString()
            }
        }
        videorate.textProperty().addListener { _, oldValue, newValue ->
            if (!intValidate(newValue.toString())) {
                videorate.text = oldValue.toString()
            }
        }
        ratenumerator.textProperty().addListener { _, oldValue, newValue ->
            if (!intValidate(newValue.toString())) {
                ratenumerator.text = oldValue.toString()
            }
        }
        ratedenominator.textProperty().addListener { _, oldValue, newValue ->
            if (!intValidate(newValue.toString())) {
                ratedenominator.text = oldValue.toString()
            }
        }
        audiorate.textProperty().addListener { _, oldValue, newValue ->
            if (!intValidate(newValue.toString())) {
                audiorate.text = oldValue.toString()
            }
        }
    }

    private fun intValidate(number: String): Boolean {
        return try {
            number.toInt()
            true
        } catch (e: NumberFormatException) {
            false
        }
    }

    @FXML
    fun handleDragOver(event: DragEvent) {
        if (event.dragboard.hasFiles())
            event.acceptTransferModes(TransferMode.COPY)
    }

    @FXML
    fun handleDropped(event: DragEvent) {
        if (event.dragboard.hasFiles()) {
            event.dragboard.files.forEach {
                event.isDropCompleted = if (!it.isDirectory && (it.extension == "mp4" || it.extension == "mov")) {
                    loadValues(it)
                    fileList.items.add(Label(it.absolutePath))
                    true
                } else {
                    false
                }
            }
        }
    }

    @FXML
    fun buttonsHandler(event: ActionEvent) {
        when ((event.source as Button).id) {
            "picker" -> getMultipleFiles()
            "clear" -> while (!fileList.items.isEmpty()) fileList.items.removeAt(0)
            "generate" -> continuousEncode()
        }
    }

    private fun continuousEncode() {
        val outputDir = getDir() ?: return
        val targetList = fileList.items.map { File(it.text) }
        launch {
            Platform.runLater { progress.isVisible = true }
            targetList.forEach {
                runBlocking { if (issameinput.isSelected) loadValues(it) }
                Platform.runLater { genelog.text = "${it.name}を変換中" }
                encodeFile(
                        it,
                        outputDir,
                        height.text.toInt(),
                        width.text.toInt(),
                        videorate.text.toLong(),
                        ratenumerator.text.toInt(),
                        ratedenominator.text.toInt(),
                        audiorate.text.toLong(),
                        isrotate.isSelected,
                        isdeinterlace.isSelected,
                        ishalfframerate.isSelected
                )
            }
            Platform.runLater { progress.isVisible = false }
            Platform.runLater { genelog.text = "完了♪" }
        }
    }

    private fun loadValues(input: File, isRotate: Boolean = isrotate.isSelected) {

        genelog.text = if (!ffprobeExec.exists()) {
            "エンコーダーがありません！"
        } else {
            "値を読み込みました"
        }

        val ffmprobe = FFprobe(ffprobeExec.absolutePath)

        val format = ffmprobe.probe(input.absolutePath)
        val videoFormat = format.streams.first { it.codec_type == FFmpegStream.CodecType.VIDEO }
        val audioFormat = format.streams.first { it.codec_type == FFmpegStream.CodecType.AUDIO }

        val oldWidth = videoFormat.width.toString()
        val oldHeight = videoFormat.height.toString()
        height.text = if (isRotate) oldWidth else oldHeight
        width.text = if (isRotate) oldHeight else oldWidth

        videorate.text = videoFormat.bit_rate.toString()
        ratenumerator.text = videoFormat.r_frame_rate.numerator.toString()
        ratedenominator.text = videoFormat.r_frame_rate.denominator.toString()
        audiorate.text = audioFormat.bit_rate.toString()

    }

    private fun getMultipleFiles() {
        val chooser = FileChooser()
        chooser.apply {
            title = "select video file(s)"
            initialDirectory = File(System.getProperty("user.dir"))
            extensionFilters.add(FileChooser.ExtensionFilter("video files", "*.mp4", "*.mov"))
        }
        val selected = chooser.showOpenMultipleDialog(mainpage.scene.window)
        if (selected.isNotEmpty()) {
            selected.forEach {
                loadValues(it)
                fileList.items.add(Label(it.absolutePath))
            }
        }
    }

    private fun getDir(): File? {
        val chooser = DirectoryChooser()
        chooser.apply {
            title = "select output dir"
            initialDirectory = File(System.getProperty("user.dir"))
        }
        return chooser.showDialog(mainpage.scene.window)
    }
}