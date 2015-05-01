# android-publisher
The android live publisher to SRS over HTTP-FLV.

## Features

* Only java files, without any native code.
* POST HTTP FLV stream to SRS.
* Hardware encoding.

## Requirements

Android SDK level 16+, Android 4.1, 4.1.1, the JELLY_BEAN

## WorkFlow

The workflow of the android publisher is:

1. Setup the Camera preview, callback with the YUV(YV12) image frame.
1. Setup the MediaCodec and MediaFormat, encode the YUV to h.264/avc in annexb.
1. Remux the annexb to flv stream.
1. [Coming soon]HTTP POST the flv stream to SRS.

Winlin 2015.5
