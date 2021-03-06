package work.airz

import javafx.scene.transform.Rotate
import net.bramp.ffmpeg.FFmpeg
import net.bramp.ffmpeg.FFmpegExecutor
import net.bramp.ffmpeg.FFprobe
import net.bramp.ffmpeg.builder.FFmpegBuilder
import net.bramp.ffmpeg.probe.FFmpegStream
import java.io.File
import java.lang.StringBuilder

val ffmpegExec = File("/usr/local/bin/ffmpeg")
val ffprobeExec = File("/usr/local/bin/ffprobe")

fun encodeFile(
        input: File,
        destFolder: File,
        height: Int,
        width: Int,
        videorate: Long,
        ratenum: Int,
        ratedeno: Int,
        audiorate: Long,
        isRotate: Boolean,
        isDeinterlace: Boolean,
        isHalfFrameRate:Boolean
) {
    val currentDir = System.getProperty("user.dir")
//    val ffmpegExec = File(currentDir + File.separator + "encoder", getExtByPlatform("ffmpeg"))
//    val ffprobeExec = File(currentDir + File.separator + "encoder", getExtByPlatform("ffprobe"))

    if (!ffmpegExec.exists() || !ffprobeExec.exists()) {
        println("encoder does not exist!")
    }

    val ffmpeg = FFmpeg(ffmpegExec.absolutePath)
    val ffmprobe = FFprobe(ffprobeExec.absolutePath)

    /**
     * formatについて
     * streams[0]がビデオ,streams[1]がオーディオ
     */
    val format = ffmprobe.probe(input.absolutePath)
    val videoFormat = format.streams.first { it.codec_type == FFmpegStream.CodecType.VIDEO }
    val audioFormat = format.streams.first { it.codec_type == FFmpegStream.CodecType.AUDIO }

    val vfargs = arrayListOf<String>()
    if (isDeinterlace) vfargs.add("yadif")
    if (isRotate) vfargs.add("transpose=2")

    val vfargString = StringBuilder()
    vfargs.joinTo(buffer = vfargString, separator = ",")

    var extraArgs = arrayListOf("-c:v", "hevc_videotoolbox", "-tag:v", "hvc1")
//    //now encoding
    val ffmpegBuilder = FFmpegBuilder()
            .setInput(input.absolutePath)
            .overrideOutputFiles(true)
            .addOutput(File(destFolder.absolutePath, "${input.nameWithoutExtension}-rotate.mp4").absolutePath)
            .setAudioChannels(audioFormat.channels)
            .setAudioCodec(audioFormat.codec_name)        // using the aac codec
            .setAudioSampleRate(audioFormat.sample_rate)  // at 48KHz
            .setAudioBitRate(audiorate)      // at 32 kbit/s
            .setPreset("slow")
            .setVideoFilter(vfargString.toString())
            .setVideoCodec("hevc")     // Video using x264
            .setConstantRateFactor(18.0) //h.265用
            .setVideoFrameRate(
                    if (isHalfFrameRate) (ratenum/2) else ratenum, //フレームレートを半減させる
                    ratedeno
            )
            .setVideoBitRate(videorate)
            .setVideoResolution(width, height) // at 640x480 resolution
            .setFormat("mp4")
            .addExtraArgs(*extraArgs.toTypedArray()) //only work Macbooks
//            .setVideoQuality(16.0)
            .done()

    val executor = FFmpegExecutor(ffmpeg, ffmprobe)
    val job = executor.createTwoPassJob(ffmpegBuilder)
    println("start encoding")
    job.run()
    println("finish")
}
