# Protocol research references

2026-10-10: reviewed gphoto/libgphoto2 master, camlibs/ptp2/config.c and library.c.

- Fuji ExposureTime numeric values and conventional shutter labels: config.c / Fuji_New_ShutterSpeed.
- Fuji download interoperability: library.c / GetPartialObject vendor exclusion, referring to issue 653.
- Capture verification: library.c / camera_fuji_capture, object-added events and before/after handles.
- X-E5 model table: USB VID 04cb, PID 0313; no capture or preview capability flags.

Links:
https://github.com/gphoto/libgphoto2/blob/master/camlibs/ptp2/config.c
https://github.com/gphoto/libgphoto2/blob/master/camlibs/ptp2/library.c
https://github.com/gphoto/libgphoto2/issues/653

This release does not link libgphoto2 or transplant its driver functions. The application-specific Java decoder and C transfer checking code were independently written using protocol facts. Copying or linking upstream implementations in a future release requires reviewing their individual LGPL headers and distribution requirements.

Only selected upstream files were fetched for research; this is not a full repository checkout or a pinned dependency.
