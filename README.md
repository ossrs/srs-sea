# SEA
The SEA(Stream Encoder for Android) publish live stream to SRS over HTTP-FLV.

## Download

The latest dev apk http://ossrs.net/apks/sea.latest.apk

## Usage

The step to use the SEA:

1. [Optional] Make sure your android is 4.1+, see [Requirements](https://github.com/simple-rtmp-server/srs-sea#requirements)
1. [Optional] Check your MediaCodec info, see [MediaCodec](https://github.com/simple-rtmp-server/srs-sea#mediacodec)
1. Download the apk from [SEA](http://ossrs.net/apks/sea.latest.apk)
1. [Optional] Setup the SRS server refer to [Stream Caster](https://github.com/simple-rtmp-server/srs/wiki/v2_CN_Streamer#push-http-flv-to-srs), or use ossrs.net server, for instance, push `http://ossrs.net:8936/live/sea.flv`
1. Play the [RTMP stream](http://www.ossrs.net/players/srs_player.html?vhost=hls&port=19351&stream=sea&server=ossrs.net&autostart=true)
1. Play the [HLS stream](http://ossrs.net:8081/live/sea.html), or scan the below code:<br/>
![SEA HLS stream](https://github.com/simple-rtmp-server/srs-sea/wiki/images/ap.sea.jpg?v=0)

## Features

* Only java files, without any native code.
* Realtime live streaming, similar to RTMP.
* Stable for POST HTTP FLV stream to [SRS](https://github.com/simple-rtmp-server/srs).
* Hardware encoding with low cpu usage.

## Requirements

Android SDK level 16+, Android 4.1, the JELLY_BEAN

## MediaCodec

To show your android [media codec info](http://ossrs.net/apks/MediaCodecInfo.apk), read [more](https://coderoid.wordpress.com/2014/08/01/obtaining-android-media-codec-information/).

## WorkFlow

The workflow of the android publisher is:

1. Setup the Camera preview, callback with the YUV(YV12) image frame.
1. Setup the MediaCodec and MediaFormat, encode the YUV to h.264/avc in annexb.
1. Remux the annexb to flv stream.
1. HTTP POST the flv stream to SRS.

## Low Latency

The latency is same to RTMP, 0.8s in lan and 3-5s in wan.

![0.8s latency](https://github.com/simple-rtmp-server/srs-sea/wiki/images/ap.delay1.jpg)

## Lightweight

![800kbps](https://github.com/simple-rtmp-server/srs-sea/wiki/images/ap.800kbps.jpg)

CPU 13% for publishing live to SRS over HTTP FLV, bitrate is 800kbps, fps is 25 and gop is 10s.

![125kbps](https://github.com/simple-rtmp-server/srs-sea/wiki/images/ap.125kbps.jpg)

CPU 6% for publishing live to SRS over HTTP FLV, bitrate is 125kbps, fps is 15 and gop is 5s.

## Links

Projects from SRS-ORG:

* SRS: https://github.com/simple-rtmp-server/srs
* SRS-BLE(pc encoder): https://github.com/simple-rtmp-server/srs-ble
* SRS-SEA(android encoder): https://github.com/simple-rtmp-server/srs-sea
* SRS-SPA(player): https://github.com/simple-rtmp-server/srs-spa
* SRS-DOCKER: https://github.com/simple-rtmp-server/srs-docker

Winlin 2015.5
