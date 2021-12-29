package reactivemongo
package api.bson

import scala.util.Random

import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
class DigestBenchmark {
  private var bytes: List[Array[Byte]] = _

  private val strings = List(
    "5AFDC56BCD0B48BFA4B9FD2059304ADF",
    "ADB4E2495E2A478595EC0338D0B5F178",
    "FF8A55CD13C749F5AEE9108CB932ED9C",
    "E9E77D882EB64ECDB7C12E9AB30D8472",
    "CECB138D8854478486F8F0D019093BC5",
    "160DCA384CB34FF9B77772EE371FE134",
    "AC7337E674D743DFA306B4DF9145BDB4",
    "7D9963FC5021485295EDD301B4322607",
    "B33C92BC851C48598C88F3BFECF6BD83",
    "E82C52896BEB4A6E82E401DE0443EDA4"
  )

  @Setup(Level.Iteration)
  def setup(): Unit = {
    val smallBlob = Array.ofDim[Byte](8)
    val mediumBlob = Array.ofDim[Byte](32)
    val bigBlob = Array.ofDim[Byte](128)
    val largeBlob = Array.ofDim[Byte](1024)

    Random.nextBytes(smallBlob)
    Random.nextBytes(mediumBlob)
    Random.nextBytes(bigBlob)
    Random.nextBytes(largeBlob)

    bytes = List(smallBlob, mediumBlob, bigBlob, largeBlob)
  }

  @Benchmark
  def hex2Str() = bytes.foreach { Digest.hex2Str(_) }

  @Benchmark
  def str2Hex() = strings.foreach { Digest.str2Hex(_) }
}
