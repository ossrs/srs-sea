# android-publisher
The android live publisher to SRS over HTTP-FLV.

## WorkFlow

The workflow of the android publisher is:

* Setup the Camera preview, callback with the YUV(YV12) image frame.
* Setup the MediaCodec and MediaFormat, encode the YUV to h.264/avc in annexb.
* Remux the annexb to flv stream.
* HTTP POST the flv stream to SRS.

Winlin 2015.5
